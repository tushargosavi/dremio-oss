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
import { Component } from "react";
//@ts-ignore
import { intl } from "@app/utils/intl";
import Modal from "components/Modals/Modal";
import FormUnsavedWarningHOC from "components/Modals/FormUnsavedWarningHOC";
import NessieDatasetSettings from "./NessieDatasetSettings";

import "./../Modal.less";

type NessieDatasetSettingsModalProps = {
  isOpen: boolean;
  hide: (arg0?: any, arg1?: boolean) => any;
  updateFormDirtyState: (arg: boolean) => void;
  modalTitle: string;
};

class NessieDatasetSettingsModal extends Component<
  NessieDatasetSettingsModalProps,
  any
> {
  render() {
    const { isOpen, hide, updateFormDirtyState, modalTitle } = this.props;
    const { formatMessage } = intl;
    return (
      <Modal
        size="large"
        title={`${formatMessage({
          id: "Dataset.Settings.for",
        })} ${modalTitle}`}
        isOpen={isOpen}
        hide={hide}
      >
        {/* @ts-ignore */}
        <NessieDatasetSettings
          hide={hide}
          updateFormDirtyState={updateFormDirtyState}
        />
      </Modal>
    );
  }
}

export default FormUnsavedWarningHOC(NessieDatasetSettingsModal);
