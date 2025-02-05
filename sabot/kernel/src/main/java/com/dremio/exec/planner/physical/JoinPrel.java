/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dremio.exec.planner.physical;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexUtil;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.util.Pair;

import com.dremio.common.exceptions.UserException;
import com.dremio.common.expression.FieldReference;
import com.dremio.common.expression.LogicalExpression;
import com.dremio.common.logical.data.JoinCondition;
import com.dremio.exec.planner.common.JoinRelBase;
import com.dremio.exec.planner.logical.ParseContext;
import com.dremio.exec.planner.logical.RexToExpr;
import com.dremio.exec.planner.physical.visitor.PrelVisitor;
import com.google.common.collect.Lists;

/**
 *
 * Base class for MergeJoinPrel, HashJoinPrel, and NestedLoopJoinPrel
 *
 */
public abstract class JoinPrel extends JoinRelBase implements Prel {
  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(JoinPrel.class);

  protected JoinPrel(RelOptCluster cluster, RelTraitSet traits, RelNode left, RelNode right, RexNode condition,
      JoinRelType joinType) {
    super(cluster, traits, left, right, condition, joinType, false);
  }

  protected static RelTraitSet adjustTraits(RelTraitSet traits) {
    // Join operators do not preserve distribution
    return JoinRelBase
        .adjustTraits(traits)
        .replaceIf(DistributionTraitDef.INSTANCE, () -> DistributionTrait.ANY);
  }

  public abstract RexNode getExtraCondition();

  @Override
  public <T, X, E extends Throwable> T accept(PrelVisitor<T, X, E> logicalVisitor, X value) throws E {
    return logicalVisitor.visitJoin(this, value);
  }

  @Override
  public Iterator<Prel> iterator() {
    return PrelUtil.iter(getLeft(), getRight());
  }

  /**
   * Check to make sure that the fields of the inputs are the same as the output field names.  If not, insert a project renaming them.
   * @param offset
   * @param input
   * @return
   */
  public RelNode getJoinInput(int offset, RelNode input) {
    assert uniqueFieldNames(input.getRowType());
    final List<String> fields = getRowType().getFieldNames();
    final List<String> inputFields = input.getRowType().getFieldNames();
    final List<String> outputFields = fields.subList(offset, offset + inputFields.size());

    if (!outputFields.equals(inputFields)) {
      // Ensure that input field names are the same as output field names.
      // If there are duplicate field names on left and right, fields will get
      // lost.
      // In such case, we need insert a rename Project on top of the input.
      return rename(input, input.getRowType().getFieldList(), outputFields);
    } else {
      return input;
    }
  }

  private RelNode rename(RelNode input, List<RelDataTypeField> inputFields, List<String> outputFieldNames) {
    List<RexNode> exprs = Lists.newArrayList();

    for (RelDataTypeField field : inputFields) {
      RexNode expr = input.getCluster().getRexBuilder().makeInputRef(field.getType(), field.getIndex());
      exprs.add(expr);
    }

    RelDataType rowType = RexUtil.createStructType(input.getCluster().getTypeFactory(), exprs, outputFieldNames);

    ProjectPrel proj = ProjectPrel.create(input.getCluster(), input.getTraitSet(), input, exprs, rowType);

    return proj;
  }

  @Override
  public boolean needsFinalColumnReordering() {
    return true;
  }

  /**
   * Build the list of join conditions for this join.
   * A join condition is built only for equality and IS NOT DISTINCT FROM comparisons. The difference is:
   * null == null is FALSE whereas null IS NOT DISTINCT FROM null is TRUE
   * For a use case of the IS NOT DISTINCT FROM comparison, see
   * {@link org.apache.calcite.rel.rules.RemoveDistinctAggregateRule}
   * @param leftFields join fields from the left input
   * @param rightFields join fields from the right input
   * @return conditions populated list of join conditions
   */
  protected List<JoinCondition> buildJoinConditions(
      List<String> leftFields,
      List<String> rightFields,
      List<Integer> leftKeys,
      List<Integer> rightKeys) {
    final List<JoinCondition> conditions = new ArrayList<>();
    List<RexNode> conjuncts = RelOptUtil.conjunctions(this.getCondition());
    short i=0;

    for (Pair<Integer, Integer> pair : Pair.zip(leftKeys, rightKeys)) {
      final RexNode conditionExpr = conjuncts.get(i++);
      final SqlKind kind  = conditionExpr.getKind();
      if (kind != SqlKind.EQUALS && kind != SqlKind.IS_NOT_DISTINCT_FROM) {
        throw UserException.unsupportedError()
            .message("Unsupported comparator in join condition %s", conditionExpr)
            .build(logger);
      }

      conditions.add(new JoinCondition(kind.toString(),
          FieldReference.getWithQuotedRef(leftFields.get(pair.left)),
          FieldReference.getWithQuotedRef(rightFields.get(pair.right))));
    }

    return conditions;
  }

  /**
   * Build extra join condition for this join for inequality expressions.
   */
  protected LogicalExpression buildExtraJoinCondition(boolean vectorize) {
    RexNode extraCondition = this.getExtraCondition();
    if (vectorize && extraCondition != null && !extraCondition.isAlwaysTrue()) {
      final PlannerSettings settings = PrelUtil.getSettings(getCluster());
      final int leftCount = left.getRowType().getFieldCount();
      final int rightCount = leftCount + right.getRowType().getFieldCount();

      // map the fields to the correct input so that RexToExpr can generate appropriate InputReferences
      IntFunction<Optional<Integer>> fieldIndexToInput = i -> {
        if (i < leftCount) {
          return Optional.of(0);
        } else if (i < rightCount) {
          return Optional.of(1);
        } else {
          throw new IllegalArgumentException("Unable to handle input number: " + i);
        }
      };

      return RexToExpr.toExpr(
        new ParseContext(settings),
        getRowType(),
        getCluster().getRexBuilder(),
        extraCondition,
        true,
        fieldIndexToInput);
    }
    return null;
  }
}
