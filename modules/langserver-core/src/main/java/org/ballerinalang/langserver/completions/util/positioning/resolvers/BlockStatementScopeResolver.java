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
import org.wso2.ballerinalang.compiler.tree.BLangStruct;
import org.wso2.ballerinalang.compiler.tree.BLangVariable;
import org.wso2.ballerinalang.compiler.tree.statements.BLangBlockStmt;
import org.wso2.ballerinalang.compiler.tree.statements.BLangCatch;
import org.wso2.ballerinalang.compiler.tree.statements.BLangIf;
import org.wso2.ballerinalang.compiler.tree.statements.BLangTransaction;
import org.wso2.ballerinalang.compiler.tree.statements.BLangTryCatchFinally;
import org.wso2.ballerinalang.compiler.util.Name;
import org.wso2.ballerinalang.compiler.util.diagnotic.DiagnosticPos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Block statement scope position resolver.
 */
public class BlockStatementScopeResolver extends CursorPositionResolver {
    /**
     * Check whether the cursor position is located before the evaluating statement node.
     * @param nodePosition position of the node
     * @param node statement being evaluated
     * @return true|false
     */
    @Override
    public boolean isCursorBeforeStatement(DiagnosticPos nodePosition, Node node, TreeVisitor treeVisitor) {
        int line = treeVisitor.getTextDocumentPositionParams().getPosition().getLine();
        int col = treeVisitor.getTextDocumentPositionParams().getPosition().getCharacter();
        DiagnosticPos zeroBasedPos = this.toZeroBasedPosition(nodePosition);
        int nodeSLine = zeroBasedPos.sLine;
        int nodeSCol = zeroBasedPos.sCol;
        // node endLine for the BLangIf node has to calculate by considering the else node. End line of the BLangIf
        // node is the endLine of the else node.
        int nodeELine = node instanceof BLangIf ? getIfElseNodeEndLine((BLangIf) node) : zeroBasedPos.eLine;
        int nodeECol = zeroBasedPos.eCol;

        BLangBlockStmt bLangBlockStmt = treeVisitor.getBlockStmtStack().peek();
        Node blockOwner = treeVisitor.getBlockOwnerStack().peek();
        int blockOwnerELine = this.getBlockOwnerELine(blockOwner, bLangBlockStmt);
        int blockOwnerECol = this.getBlockOwnerECol(blockOwner, bLangBlockStmt);

        boolean isLastStatement = this.isNodeLastStatement(bLangBlockStmt, blockOwner, node);

        if (line < nodeSLine || (line == nodeSLine && col < nodeSCol) ||
                (isLastStatement && (line < blockOwnerELine || (line == blockOwnerELine && col <= blockOwnerECol)) &&
                        (line > nodeELine || (line == nodeELine && col > nodeECol)))) {
            Map<Name, Scope.ScopeEntry> visibleSymbolEntries =
                    treeVisitor.resolveAllVisibleSymbols(treeVisitor.getSymbolEnv());
            treeVisitor.populateSymbols(visibleSymbolEntries, null);
            treeVisitor.setTerminateVisitor(true);
            return true;
        }

        return false;
    }

    private boolean isNodeLastStatement(BLangBlockStmt bLangBlockStmt, Node blockOwner, Node node) {
        if (bLangBlockStmt != null) {
            return (bLangBlockStmt.stmts.indexOf(node) == (bLangBlockStmt.stmts.size() - 1));
        } else if (blockOwner instanceof BLangStruct) {
            List<BLangVariable> structFields = ((BLangStruct) blockOwner).getFields();
            return (structFields.indexOf(node) == structFields.size() - 1);
        } else {
            return false;
        }
    }

    private int getBlockOwnerELine(Node blockOwner, BLangBlockStmt bLangBlockStmt) {
        if (blockOwner instanceof BLangTryCatchFinally) {
            return getTryCatchBlockComponentEndLine((BLangTryCatchFinally) blockOwner, bLangBlockStmt);
        } else if (blockOwner == null) {
            // When the else node is evaluating, block owner is null and the block statement only present
            // This is because, else node is represented with a blocks statement only
            return bLangBlockStmt.getPosition().getEndLine();
        } else if (blockOwner instanceof BLangTransaction) {
            return this.getTransactionBlockComponentEndLine((BLangTransaction) blockOwner, bLangBlockStmt);
        } else {
            return blockOwner.getPosition().getEndLine();
        }
    }

    private int getBlockOwnerECol(Node blockOwner, BLangBlockStmt bLangBlockStmt) {
        if (blockOwner instanceof BLangTryCatchFinally) {
            return getTryCatchBlockComponentEndCol((BLangTryCatchFinally) blockOwner, bLangBlockStmt);
        } else if (blockOwner == null) {
            // When the else node is evaluating, block owner is null and the block statement only present
            // This is because, else node is represented with a blocks statement only
            return bLangBlockStmt.getPosition().endColumn();
        } else {
            return blockOwner.getPosition().endColumn();
        }
    }

    private int getTryCatchBlockComponentEndLine(BLangTryCatchFinally tryCatchFinally, BLangBlockStmt blockStmt) {
        if (blockStmt == tryCatchFinally.tryBody) {
            // We are inside the try block
            if (tryCatchFinally.catchBlocks.size() > 0) {
                BLangCatch bLangCatch = tryCatchFinally.catchBlocks.get(0);
                return bLangCatch.getPosition().sLine;
            } else if (tryCatchFinally.finallyBody != null) {
                return tryCatchFinally.finallyBody.getPosition().sLine;
            } else {
                return tryCatchFinally.getPosition().eLine;
            }
        } else {
            // We are inside the finally block
            return tryCatchFinally.getPosition().eLine;
        }
    }

    private int getTryCatchBlockComponentEndCol(BLangTryCatchFinally tryCatchFinally, BLangBlockStmt blockStmt) {
        if (blockStmt == tryCatchFinally.tryBody) {
            // We are inside the try block
            if (tryCatchFinally.catchBlocks.size() > 0) {
                BLangCatch bLangCatch = tryCatchFinally.catchBlocks.get(0);
                return bLangCatch.getPosition().sCol;
            } else if (tryCatchFinally.finallyBody != null) {
                return tryCatchFinally.finallyBody.getPosition().sCol;
            } else {
                return tryCatchFinally.getPosition().eCol;
            }
        } else {
            // We are inside the finally block
            return tryCatchFinally.getPosition().eCol;
        }
    }

    private int getTransactionBlockComponentEndLine(BLangTransaction bLangTransaction, BLangBlockStmt bLangBlockStmt) {
        BLangBlockStmt transactionBody = bLangTransaction.transactionBody;
        BLangBlockStmt committedBody = bLangTransaction.committedBody;
        BLangBlockStmt failedBody = bLangTransaction.failedBody;
        BLangBlockStmt abortedBody = bLangTransaction.abortedBody;

        List<BLangBlockStmt> components = new ArrayList<>();
        components.add(transactionBody);
        components.add(committedBody);
        components.add(failedBody);
        components.add(abortedBody);

        components.sort(Comparator.comparing(component -> {
            if (component != null) {
                return component.getPosition().getEndLine();
            } else {
                return -1;
            }
        }));

        int blockStmtIndex = components.indexOf(bLangBlockStmt);
        if (blockStmtIndex == components.size() - 1) {
            return bLangTransaction.getPosition().eLine;
        } else if (components.get(blockStmtIndex + 1) != null) {
            return components.get(blockStmtIndex + 1).getPosition().sLine;
        } else {
            // Ideally should not invoke this
            return -1;
        }
    }

    /**
     * Calculate the end line of the BLangIf node.
     * @param bLangIf {@link BLangIf}
     * @return end line of the if node
     */
    private int getIfElseNodeEndLine(BLangIf bLangIf) {
        BLangIf ifNode = bLangIf;
        while (true) {
            if (ifNode.elseStmt == null) {
                return bLangIf.getPosition().eLine;
            } else if (ifNode.elseStmt instanceof BLangIf) {
                ifNode = (BLangIf) ifNode.elseStmt;
            } else {
                return ifNode.elseStmt.getPosition().getEndLine();
            }
        }
    }
}
