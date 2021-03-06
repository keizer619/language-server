/*
*  Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/
package org.ballerinalang.langserver.completions.resolvers.parsercontext;

import org.ballerinalang.langserver.completions.SuggestionsFilterDataModel;
import org.ballerinalang.langserver.completions.consts.ItemResolverConstants;
import org.ballerinalang.langserver.completions.consts.Priority;
import org.ballerinalang.langserver.completions.resolvers.AbstractItemResolver;
import org.eclipse.lsp4j.CompletionItem;

import java.util.ArrayList;

/**
 * annotation body context resolver for the completion items.
 */
public class ParserRuleAttachmentPointContextResolver extends AbstractItemResolver {
    @Override
    public ArrayList<CompletionItem> resolveItems(SuggestionsFilterDataModel dataModel) {
        ArrayList<CompletionItem> completionItems = new ArrayList<>();
        completionItems.add(populateCompletionItem(ItemResolverConstants.ACTION,
                ItemResolverConstants.KEYWORD_TYPE, Priority.PRIORITY7.name(), ItemResolverConstants.ACTION));
        completionItems.add(populateCompletionItem(ItemResolverConstants.ANNOTATION,
                ItemResolverConstants.KEYWORD_TYPE, Priority.PRIORITY7.name(),
                ItemResolverConstants.ANNOTATION));
        completionItems.add(populateCompletionItem(ItemResolverConstants.CONNECTOR,
                ItemResolverConstants.KEYWORD_TYPE, Priority.PRIORITY7.name(), ItemResolverConstants.CONNECTOR));
        completionItems.add(populateCompletionItem(ItemResolverConstants.CONST,
                ItemResolverConstants.KEYWORD_TYPE, Priority.PRIORITY7.name(), ItemResolverConstants.CONST));
        completionItems.add(populateCompletionItem(ItemResolverConstants.FUNCTION,
                ItemResolverConstants.KEYWORD_TYPE, Priority.PRIORITY7.name(), ItemResolverConstants.FUNCTION));
        completionItems.add(populateCompletionItem(ItemResolverConstants.RESOURCE,
                ItemResolverConstants.KEYWORD_TYPE, Priority.PRIORITY7.name(), ItemResolverConstants.RESOURCE));
        completionItems.add(populateCompletionItem(ItemResolverConstants.SERVICE,
                ItemResolverConstants.KEYWORD_TYPE, Priority.PRIORITY7.name(), ItemResolverConstants.SERVICE));
        completionItems.add(populateCompletionItem(ItemResolverConstants.STRUCT,
                ItemResolverConstants.KEYWORD_TYPE, Priority.PRIORITY7.name(), ItemResolverConstants.STRUCT));

        return completionItems;
    }
}
