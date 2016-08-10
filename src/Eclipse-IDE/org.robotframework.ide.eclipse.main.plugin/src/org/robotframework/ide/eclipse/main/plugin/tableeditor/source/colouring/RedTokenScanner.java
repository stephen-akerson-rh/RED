package org.robotframework.ide.eclipse.main.plugin.tableeditor.source.colouring;

import static com.google.common.collect.Lists.newArrayList;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.text.rules.Token;
import org.rf.ide.core.testdata.text.read.IRobotLineElement;
import org.rf.ide.core.testdata.text.read.RobotLine;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.source.DocumentUtilities;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.source.RobotDocument;
import org.robotframework.ide.eclipse.main.plugin.tableeditor.source.colouring.ISyntaxColouringRule.PositionedTextToken;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;

public class RedTokenScanner implements ITokenScanner {

    private RobotDocument document;

    private final List<ISyntaxColouringRule> rules;

    private Deque<IRobotLineElement> tokensToAnalyze;
    private List<IRobotLineElement> analyzedTokens;

    private Position lastTokenPosition;

    private int rangeOffset;
    private int rangeLength;
    private int rangeLine;
    private int currentOffsetInToken;

    public RedTokenScanner(final ISyntaxColouringRule... rules) {
        this.rules = newArrayList(rules);
    }

    @Override
    public void setRange(final IDocument document, final int offset, final int length) {
        this.document = (RobotDocument) document;

        this.tokensToAnalyze = null;
        this.analyzedTokens = new ArrayList<>();

        this.rangeOffset = offset;
        this.rangeLength = length;
        this.rangeLine = DocumentUtilities.getLine(document, offset);

        this.currentOffsetInToken = 0;
    }

    @Override
    public IToken nextToken() {
        return nextToken(new Supplier<Deque<IRobotLineElement>>() {

            @Override
            public Deque<IRobotLineElement> get() {
                final List<RobotLine> lines = document.getNewestModel().getFileContent();
                return new RedTokensQueueBuilder().buildQueue(rangeOffset, rangeLength, lines, rangeLine);
            }
        });
    }

    @VisibleForTesting
    IToken nextToken(final Supplier<Deque<IRobotLineElement>> elementsQueueSupplier) {
        if (tokensToAnalyze == null) {
            tokensToAnalyze = elementsQueueSupplier.get();

            final IRobotLineElement firstToken = tokensToAnalyze.peekFirst();
            if (firstToken != null) {
                currentOffsetInToken = rangeOffset - firstToken.getStartOffset();
            }
        }

        if (tokensToAnalyze.isEmpty()) {
            return Token.EOF;
        }
        final IRobotLineElement nextToken = tokensToAnalyze.poll();

        for (final ISyntaxColouringRule rule : rules) {
            if (!rule.isApplicable(nextToken)) {
                continue;
            }
            final Optional<PositionedTextToken> tok = rule.evaluate(nextToken, currentOffsetInToken, analyzedTokens);
            if (tok.isPresent()) {
                final PositionedTextToken textToken = tok.get();
                lastTokenPosition = textToken.getPosition();

                if (lastTokenPosition.offset + lastTokenPosition.length >= nextToken.getStartOffset()
                        + nextToken.getText().length()) {
                    // rule have consumed whole Robot Token
                    currentOffsetInToken = 0;
                    analyzedTokens.add(nextToken);
                } else {
                    // the token needs more coloring, so return it to queue and shift the
                    // offset
                    currentOffsetInToken = lastTokenPosition.getOffset() + lastTokenPosition.getLength()
                            - nextToken.getStartOffset();
                    tokensToAnalyze.addFirst(nextToken);
                }
                return textToken.getToken();
            }
        }
        lastTokenPosition = new Position(nextToken.getStartOffset(), nextToken.getText().length());
        currentOffsetInToken = 0;
        analyzedTokens.add(nextToken);
        return ISyntaxColouringRule.DEFAULT_TOKEN;
    }

    @Override
    public int getTokenOffset() {
        return lastTokenPosition.getOffset();
    }

    @Override
    public int getTokenLength() {
        return lastTokenPosition.getLength();
    }
}
