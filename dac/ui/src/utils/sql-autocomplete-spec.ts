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

import {
  constructTransformValues,
  getAutoCompleteInsertText,
} from "./sql-autocomplete";
import { expect } from "chai";

const subEntityExamples = [
  {
    contentWithPairedQuotes: ['select * from "samples or"'],
    contentWithSoloQuote: ['select * from "samples or'],
    position: { lineNumber: 1, column: 26 },
    entity: '"samples original"',
    activeWord: "or",
  },
  {
    contentWithPairedQuotes: ['select * from "samples.or"'],
    contentWithSoloQuote: ['select * from "samples.or'],
    position: { lineNumber: 1, column: 26 },
    entity: '"samples.original"',
    activeWord: "or",
  },
];

describe("sql-autocomplete", () => {
  const transformCases = {
    insidePairedDoubleQuote: false,
    insideSoloDoubleQuote: false,
    replaceWithSubEntity: false,
    entityLeftOfCursor: "",
  };

  describe("constructTransformValues", () => {
    const position = { lineNumber: 1, column: 26 };

    it("should return the false values if no transformation is required", () => {
      const content = ["select * from Samples.SF"];
      expect(constructTransformValues(content, position, "SF")).to.deep.equal({
        ...transformCases,
        entityLeftOfCursor: content[0],
      });
    });

    it("should identify that cursor is in a pair double quotes", () => {
      const content = ['select * from Samples."SF"'];
      expect(constructTransformValues(content, position, "SF")).to.deep.equal({
        ...transformCases,
        insidePairedDoubleQuote: true,
        entityLeftOfCursor: "SF",
      });
    });

    it("should identify that cursor is right of a solo double quote", () => {
      const content = ['select * from Samples."SF'];
      expect(constructTransformValues(content, position, "SF")).to.deep.equal({
        ...transformCases,
        insideSoloDoubleQuote: true,
        entityLeftOfCursor: "SF",
      });
    });

    it("should identify that the cursor for a desired entity follows an entity divider", () => {
      const content = ['select * from Samples."SF Univer'];
      expect(
        constructTransformValues(content, { ...position, column: 33 }, "Univer")
      ).to.deep.equal({
        ...transformCases,
        insideSoloDoubleQuote: true,
        entityLeftOfCursor: "SF Univer",
        replaceWithSubEntity: true,
      });
    });
  });

  describe("getAutoCompleteInsertText", () => {
    it("should return the original insertText (without double quotes) if no transform is required", () => {
      expect(
        getAutoCompleteInsertText("samples", transformCases, "samples")
      ).to.equal("samples");
    });

    it("should return the original insertText (with double quotes) if no transform is required", () => {
      expect(
        getAutoCompleteInsertText('"samples"', transformCases, '"samples"')
      ).to.equal('"samples"');
    });

    it("should return insertText with only the right double quote", () => {
      expect(
        getAutoCompleteInsertText(
          '"samples"',
          { ...transformCases, insideSoloDoubleQuote: true },
          '"samples"'
        )
      ).to.equal('samples"');
    });

    it("should return insertText with neither of the double quotes", () => {
      expect(
        getAutoCompleteInsertText(
          '"samples"',
          { ...transformCases, insidePairedDoubleQuote: true },
          '"samples"'
        )
      ).to.equal("samples");
    });

    subEntityExamples.forEach((example: any) => {
      it(`using ${example.entity}, it should return insertText with only the remaining subentities and no double quotes`, () => {
        const subTransformCases = constructTransformValues(
          example.contentWithPairedQuotes,
          example.position,
          example.activeWord
        );
        expect(
          getAutoCompleteInsertText(
            example.entity,
            subTransformCases,
            example.activeWord
          )
        ).to.equal("original");
      });

      it(`using ${example.entity}, it should return insertText with only the remaining subentities and with the right double quote`, () => {
        const subTransformCases = constructTransformValues(
          example.contentWithSoloQuote,
          example.position,
          example.activeWord
        );
        expect(
          getAutoCompleteInsertText(
            example.entity,
            subTransformCases,
            example.activeWord
          )
        ).to.equal('original"');
      });
    });
  });
});
