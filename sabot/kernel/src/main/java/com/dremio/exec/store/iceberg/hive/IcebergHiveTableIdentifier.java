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
package com.dremio.exec.store.iceberg.hive;

import com.dremio.exec.store.iceberg.model.IcebergTableIdentifier;

/**
 * Hive based iceberg table identifier
 */
public class IcebergHiveTableIdentifier implements IcebergTableIdentifier {
  private final String tableFolder;
  private final String namespace;
  private final String tableName;

  public IcebergHiveTableIdentifier(String namespace, String tableFolder, String tableName) {
    this.namespace = namespace;
    this.tableFolder = tableFolder;
    this.tableName = tableName;
  }

  public String getTableFolder() {
    return tableFolder;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getTableName() {
    return tableName;
  }
}
