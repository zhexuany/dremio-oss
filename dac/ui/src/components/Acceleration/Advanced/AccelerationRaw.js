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
import PropTypes from "prop-types";
import Immutable from "immutable";
import clsx from "clsx";

import FontIcon from "components/Icon/FontIcon";
import SimpleButton from "components/Buttons/SimpleButton";
import { createReflectionFormValues } from "utils/accelerationUtils";
import AccelerationRawMixin from "@inject/components/Acceleration/Advanced/AccelerationRawMixin.js";

import "@app/uiTheme/less/Acceleration/Acceleration.less";
import { commonThemes } from "../commonThemes";
import AccelerationGridController from "./AccelerationGridController";
import * as classes from "@app/uiTheme/radium/replacingRadiumPseudoClasses.module.less";

@AccelerationRawMixin
export default class AccelerationRaw extends Component {
  static propTypes = {
    dataset: PropTypes.instanceOf(Immutable.Map).isRequired,
    reflections: PropTypes.instanceOf(Immutable.Map).isRequired,
    fields: PropTypes.object,
    canAlter: PropTypes.any,
  };

  static getFields() {
    return [
      "rawReflections[].id",
      "rawReflections[].tag",
      "rawReflections[].type",
      "rawReflections[].name",
      "rawReflections[].enabled",
      "rawReflections[].arrowCachingEnabled",
      "rawReflections[].partitionDistributionStrategy",
      "rawReflections[].partitionFields[].name",
      "rawReflections[].sortFields[].name",
      "rawReflections[].displayFields[].name",
      "rawReflections[].distributionFields[].name",
      "rawReflections[].shouldDelete",
    ];
  }

  static validate() {
    return {};
  }

  addNewLayout = () => {
    const { rawReflections } = this.props.fields;

    const reflection = createReflectionFormValues(
      {
        type: "RAW",
      },
      rawReflections.map((e) => e.name.value)
    );

    rawReflections.addField(reflection);
  };

  renderHeader = () => {
    return (
      <div className={"AccelerationRaw__header"}>
        <h3 className={"AccelerationRaw__toggleLabel"}>
          <FontIcon type="RawMode" theme={commonThemes.rawIconTheme} />
          {la("Raw Reflections")}
        </h3>
        <SimpleButton
          onClick={this.addNewLayout}
          buttonStyle="secondary"
          className={clsx(classes["secondaryButtonPsuedoClasses"])}
          // DX-34369
          style={
            this.checkIfButtonShouldBeRendered()
              ? { minWidth: "110px", marginRight: "8px" }
              : { display: "none" }
          }
          type="button"
        >
          {la("New Reflection")}
        </SimpleButton>
      </div>
    );
  };

  render() {
    const {
      dataset,
      reflections,
      fields: { rawReflections },
      canAlter,
    } = this.props;
    return (
      <div className={"AccelerationRaw"}>
        {this.renderHeader()}
        <AccelerationGridController
          canAlter={canAlter}
          dataset={dataset}
          reflections={reflections}
          layoutFields={rawReflections}
          activeTab="raw"
        />
      </div>
    );
  }
}
