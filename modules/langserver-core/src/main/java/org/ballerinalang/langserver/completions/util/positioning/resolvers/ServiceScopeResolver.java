/*
 * Copyright (c) 2017, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ballerinalang.langserver.completions.util.positioning.resolvers;

import org.ballerinalang.langserver.completions.TreeVisitor;
import org.ballerinalang.model.tree.Node;
import org.wso2.ballerinalang.compiler.semantics.model.Scope;
import org.wso2.ballerinalang.compiler.util.Name;
import org.wso2.ballerinalang.compiler.util.diagnotic.DiagnosticPos;

import java.util.Map;

/**
 * Service scope position resolver.
 */
public class ServiceScopeResolver extends CursorPositionResolver {
    @Override
    public boolean isCursorBeforeStatement(DiagnosticPos nodePosition, Node node, TreeVisitor treeVisitor) {
        int line = treeVisitor.getTextDocumentPositionParams().getPosition().getLine();
        int col = treeVisitor.getTextDocumentPositionParams().getPosition().getCharacter();
        DiagnosticPos zeroBasedPo = this.toZeroBasedPosition(nodePosition);
        int nodeSLine = zeroBasedPo.sLine;
        int nodeSCol = zeroBasedPo.sCol;

        if (line < nodeSLine || (line == nodeSLine && col < nodeSCol)) {
            Map<Name, Scope.ScopeEntry> visibleSymbolEntries =
                    treeVisitor.resolveAllVisibleSymbols(treeVisitor.getSymbolEnv());
            treeVisitor.populateSymbols(visibleSymbolEntries, null);
            treeVisitor.setTerminateVisitor(true);
            return true;
        }

        return false;
    }
}
