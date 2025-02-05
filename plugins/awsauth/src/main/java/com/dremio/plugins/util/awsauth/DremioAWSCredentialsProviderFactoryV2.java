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
package com.dremio.plugins.util.awsauth;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.s3a.AnonymousAWSCredentialsProvider;
import org.apache.hadoop.fs.s3a.Constants;
import org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider;

import com.dremio.aws.SharedInstanceProfileCredentialsProvider;
import com.dremio.service.coordinator.DremioAssumeRoleCredentialsProviderV2;

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/**
 * Factory to provide the appropriate AWSCredentialsProvider based on a Configuration.
 *
 * Note: This package can hopefully be used in the future to consolidate very
 * similar code in S3, Hive, and Glue plugins, but that was out of scope for
 * the current work.
 */
public final class DremioAWSCredentialsProviderFactoryV2 {

  // AWS Credential providers
  public static final String ACCESS_KEY_PROVIDER = SimpleAWSCredentialsProvider.NAME;
  public static final String EC2_METADATA_PROVIDER = "com.amazonaws.auth.InstanceProfileCredentialsProvider";
  public static final String NONE_PROVIDER = AnonymousAWSCredentialsProvider.NAME;
  public static final String ASSUME_ROLE_PROVIDER = "com.dremio.plugins.s3.store.STSCredentialProviderV1";
  // Credential provider for DCS data roles
  public static final String DREMIO_ASSUME_ROLE_PROVIDER = "com.dremio.service.coordinator" +
    ".DremioAssumeRoleCredentialsProviderV1";
  public static final String GLUE_DREMIO_ASSUME_ROLE_PROVIDER = "com.dremio.exec.store.hive.GlueDremioAssumeRoleCredentialsProviderV1";
  public static final String AWS_PROFILE_PROVIDER = "com.dremio.plugins.s3.store.AWSProfileCredentialsProviderV1";


  private DremioAWSCredentialsProviderFactoryV2() {
  }

  /**
   * Constructs and returns the appropriate AWSCredentialsProvider.
   * @return
   */
  public static AwsCredentialsProvider getAWSCredentialsProvider(Configuration config) {

    switch(config.get(Constants.AWS_CREDENTIALS_PROVIDER)) {
      case ACCESS_KEY_PROVIDER:
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(
          config.get(Constants.ACCESS_KEY), config.get(Constants.SECRET_KEY)));
      case EC2_METADATA_PROVIDER:
        return new SharedInstanceProfileCredentialsProvider();
      case NONE_PROVIDER:
        return AnonymousCredentialsProvider.create();
      case DREMIO_ASSUME_ROLE_PROVIDER:
        return new DremioAssumeRoleCredentialsProviderV2();
      case GLUE_DREMIO_ASSUME_ROLE_PROVIDER:
        return new GlueDremioAssumeRoleCredentialsProviderV2();
      case ASSUME_ROLE_PROVIDER:
        return new STSCredentialProviderV2(config);
      case AWS_PROFILE_PROVIDER:
        return new AWSProfileCredentialsProviderV2(config);
      default:
        throw new IllegalStateException("Invalid AWSCredentialsProvider provided: " + config.get(Constants.AWS_CREDENTIALS_PROVIDER));
    }
  }
}
