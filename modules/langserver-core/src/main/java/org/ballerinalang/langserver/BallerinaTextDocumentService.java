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
package org.ballerinalang.langserver;

import org.antlr.v4.runtime.DefaultErrorStrategy;
import org.ballerinalang.compiler.CompilerPhase;
import org.ballerinalang.langserver.completions.BallerinaCustomErrorStrategy;
import org.ballerinalang.langserver.completions.SuggestionsFilterDataModel;
import org.ballerinalang.langserver.completions.TreeVisitor;
import org.ballerinalang.langserver.completions.consts.CompletionItemResolver;
import org.ballerinalang.langserver.completions.resolvers.TopLevelResolver;
import org.ballerinalang.langserver.completions.util.TextDocumentServiceUtil;
import org.ballerinalang.langserver.workspace.WorkspaceDocumentManager;
import org.ballerinalang.langserver.workspace.WorkspaceDocumentManagerImpl;
import org.ballerinalang.langserver.workspace.repository.WorkspacePackageRepository;
import org.ballerinalang.repository.PackageRepository;
import org.ballerinalang.util.diagnostic.DiagnosticListener;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.ballerinalang.compiler.Compiler;
import org.wso2.ballerinalang.compiler.tree.BLangNode;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.CompilerOptions;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.ballerinalang.compiler.CompilerOptionName.COMPILER_PHASE;
import static org.ballerinalang.compiler.CompilerOptionName.SOURCE_ROOT;

/**
 * Text document service implementation for ballerina.
 */
public class BallerinaTextDocumentService implements TextDocumentService {

    private final BallerinaLanguageServer ballerinaLanguageServer;
    private final WorkspaceDocumentManager documentManager;
    private static final Logger LOGGER = LoggerFactory.getLogger(BallerinaTextDocumentService.class);

    public BallerinaTextDocumentService(BallerinaLanguageServer ballerinaLanguageServer) {
        this.ballerinaLanguageServer = ballerinaLanguageServer;
        this.documentManager = new WorkspaceDocumentManagerImpl();
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>>
    completion(TextDocumentPositionParams position) {
        return CompletableFuture.supplyAsync(() -> {
            SuggestionsFilterDataModel filterDataModel = new SuggestionsFilterDataModel();
            List<CompletionItem> completions;
            String uri = position.getTextDocument().getUri();
            String fileContent = this.documentManager.getFileContent(Paths.get(URI.create(uri)));
            Path filePath = this.getPath(uri);
            String[] pathComponents = position.getTextDocument().getUri().split("\\" + File.separator);
            String fileName = pathComponents[pathComponents.length - 1];

            String pkgName = TextDocumentServiceUtil.getPackageFromContent(fileContent);
            String sourceRoot = TextDocumentServiceUtil.getSourceRoot(filePath, pkgName);

            PackageRepository packageRepository = new WorkspacePackageRepository(sourceRoot, documentManager);
            CompilerContext compilerContext = prepareCompilerContext(packageRepository, sourceRoot);

            List<org.ballerinalang.util.diagnostic.Diagnostic> balDiagnostics = new ArrayList<>();
            CollectDiagnosticListener diagnosticListener = new CollectDiagnosticListener(balDiagnostics);
            BallerinaCustomErrorStrategy customErrorStrategy = new BallerinaCustomErrorStrategy(compilerContext,
                    position, filterDataModel);
            compilerContext.put(DiagnosticListener.class, diagnosticListener);
            compilerContext.put(DefaultErrorStrategy.class, customErrorStrategy);

            Compiler compiler = Compiler.getInstance(compilerContext);
            if ("".equals(pkgName)) {
                compiler.compile(fileName);
            } else {
                compiler.compile(pkgName);
            }

            BLangPackage bLangPackage = (BLangPackage) compiler.getAST();

            // Visit the package to resolve the symbols
            TreeVisitor treeVisitor = new TreeVisitor(fileName, compilerContext, position, filterDataModel);
            bLangPackage.accept(treeVisitor);

            BLangNode symbolEnvNode = filterDataModel.getSymbolEnvNode();
            if (symbolEnvNode == null) {
                completions = CompletionItemResolver.getResolverByClass(TopLevelResolver.class)
                        .resolveItems(filterDataModel);
            } else {
                completions = CompletionItemResolver.getResolverByClass(symbolEnvNode.getClass())
                        .resolveItems(filterDataModel);
            }
            return Either.forLeft(completions);
        });
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
        return null;
    }

    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
        return CompletableFuture.supplyAsync(() -> null);
    }

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
        return CompletableFuture.supplyAsync(() -> null);
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return CompletableFuture.supplyAsync(() -> null);
    }

    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(
            TextDocumentPositionParams position) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
        return CompletableFuture.supplyAsync(() -> null);
    }

    @Override
    public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
        return CompletableFuture.supplyAsync(() ->
            params.getContext().getDiagnostics().stream()
            .map(diagnostic -> {
                List<Command> res = new ArrayList<>();
                return res.stream();
            })
            .flatMap(it -> it)
            .collect(Collectors.toList())
        );
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        return null;
    }

    @Override
    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
        return null;
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        return null;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        Path openedPath = this.getPath(params.getTextDocument().getUri());
        if (openedPath == null) {
            return;
        }

        this.documentManager.openFile(openedPath, params.getTextDocument().getText());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        Path changedPath = this.getPath(params.getTextDocument().getUri());
        String content = params.getContentChanges().get(0).getText();

        String pkgName = TextDocumentServiceUtil.getPackageFromContent(content);
        String sourceRoot = TextDocumentServiceUtil.getSourceRoot(changedPath, pkgName);

        PackageRepository packageRepository = new WorkspacePackageRepository(sourceRoot, documentManager);
        CompilerContext context = prepareCompilerContext(packageRepository, sourceRoot);

        List<org.ballerinalang.util.diagnostic.Diagnostic> balDiagnostics = new ArrayList<>();
        CollectDiagnosticListener diagnosticListener = new CollectDiagnosticListener(balDiagnostics);
        context.put(DiagnosticListener.class, diagnosticListener);

        Compiler compiler = Compiler.getInstance(context);
        if ("".equals(pkgName)) {
            String[] pathComponents = params.getTextDocument().getUri().split("\\" + File.separator);
            String fileName = pathComponents[pathComponents.length - 1];
            compiler.compile(fileName);
        } else {
            compiler.compile(pkgName);
        }

        PublishDiagnosticsParams diagnostics = new PublishDiagnosticsParams();
        Diagnostic d = new Diagnostic();
        d.setSeverity(DiagnosticSeverity.Error);
        Range r = new Range();
        r.setStart(new Position(1, 1));
        r.setEnd(new Position(2, 1));
        d.setRange(r);
        d.setMessage("some error message");
        diagnostics.setDiagnostics(Arrays.asList(d));
        diagnostics.setUri(params.getTextDocument().getUri());
        this.ballerinaLanguageServer.getClient().publishDiagnostics(diagnostics);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        Path closedPath = this.getPath(params.getTextDocument().getUri());
        if (closedPath == null) {
            return;
        }

        this.documentManager.closeFile(this.getPath(params.getTextDocument().getUri()));
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
    }

    private Path getPath(String uri) {
        Path path = null;
        try {
            path = Paths.get(new URL(uri).toURI());
        } catch (URISyntaxException | MalformedURLException e) {
            LOGGER.error(e.getMessage());
        } finally {
            return path;
        }
    }

    protected CompilerContext prepareCompilerContext(PackageRepository packageRepository, String sourceRoot) {
        CompilerContext context = new CompilerContext();
        context.put(PackageRepository.class, packageRepository);
        CompilerOptions options = CompilerOptions.getInstance(context);
        options.put(SOURCE_ROOT, sourceRoot);
        options.put(COMPILER_PHASE, CompilerPhase.CODE_ANALYZE.toString());
        return context;
    }
}
