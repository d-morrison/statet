/*=============================================================================#
 # Copyright (c) 2007-2015 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.core.rsource.ast;

import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS123_SYNTAX_SEQREL_UNEXPECTED;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS12_SYNTAX_TOKEN_UNEXPECTED;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS12_SYNTAX_TOKEN_UNKNOWN;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS2_SYNTAX_CC_NOT_CLOSED;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS2_SYNTAX_CONDITION_MISSING;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS2_SYNTAX_CONDITION_NOT_CLOSED;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS2_SYNTAX_ELEMENTNAME_MISSING;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS2_SYNTAX_EXPR_AS_BODY_MISSING;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS2_SYNTAX_EXPR_AS_CONDITION_MISSING;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS2_SYNTAX_EXPR_BEFORE_OP_MISSING;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS2_SYNTAX_FCALL_NOT_CLOSED;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS2_SYNTAX_FDEF_ARGS_MISSING;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS2_SYNTAX_FDEF_ARGS_NOT_CLOSED;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS2_SYNTAX_IF_MISSING;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS2_SYNTAX_IN_MISSING;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS2_SYNTAX_OPERATOR_MISSING;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS2_SYNTAX_SUBINDEXED_NOT_CLOSED;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS2_SYNTAX_SYMBOL_MISSING;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS3_FDEF;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS3_FOR;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS3_IF;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS3_WHILE;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUSFLAG_ERROR_IN_CHILD;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUSFLAG_REAL_ERROR;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUSFLAG_SUBSEQUENT;
import static de.walware.statet.r.core.rsource.IRSourceConstants.STATUS_RUNTIME_ERROR;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import de.walware.ecommons.ltk.AstInfo;
import de.walware.ecommons.ltk.ast.IAstNode;
import de.walware.ecommons.string.IStringFactory;
import de.walware.ecommons.string.StringFactory;
import de.walware.ecommons.text.core.input.TextParserInput;

import de.walware.statet.r.core.rlang.RTerminal;
import de.walware.statet.r.core.rsource.RLexer;
import de.walware.statet.r.core.rsource.ast.RAstNode.Assoc;
import de.walware.statet.r.internal.core.RCorePlugin;


/**
 * Scanner to create a R AST.
 */
public final class RScanner {
	
	
	private static final byte LINE_MODE_CONSOLE = 1;
	private static final byte LINE_MODE_BLOCK = 2;
	private static final byte LINE_MODE_EAT = 3;
	
	public static final int LEXER_CONFIG= RLexer.SKIP_WHITESPACE;
	
	
	private static final class ExprContext {
		final RAstNode rootNode;
		final Expression rootExpr;
		RAstNode lastNode;
		Expression openExpr;
		final byte lineMode;
		
		public ExprContext(final RAstNode node, final Expression expr, final byte eatLines) {
			this.rootNode = this.lastNode = node;
			this.rootExpr = this.openExpr = expr;
			this.lineMode = eatLines;
		}
		
		final void update(final RAstNode lastNode, final Expression openExpr) {
			this.lastNode = lastNode;
			if (openExpr == null || openExpr.node != null) {
				this.openExpr = null;
			}
			else {
				this.openExpr = openExpr;
			}
		}
	}
	
	private static final class RoxygenCollector {
		
		private Comment[] fLines = new Comment[64];
		private int fLineCount;
		private DocuComment fCurrent;
		
		void init() {
			this.fLineCount = 0;
			this.fCurrent = null;
		}
		
		void add(final Comment comment) {
			if (this.fCurrent == null) {
				this.fCurrent = new DocuComment();
			}
			comment.fRParent = this.fCurrent;
			
			if (this.fLineCount == this.fLines.length) {
				this.fLines = Arrays.copyOf(this.fLines, this.fLineCount+64);
			}
			this.fLines[this.fLineCount++] = comment;
		}
		
		boolean hasComment() {
			return (this.fCurrent != null);
		}
		
		DocuComment finish(final RLexer lexer) {
			final DocuComment comment = new DocuComment();
			final Comment[] lines = Arrays.copyOf(this.fLines, this.fLineCount);
			comment.fLines = lines;
			comment.fStartOffset = lines[0].fStartOffset;
			comment.fStopOffset = lines[this.fLineCount-1].fStopOffset;
			comment.fNextOffset = (lexer != null && lexer.getType() != RTerminal.EOF) ? lexer.getOffset() : Integer.MIN_VALUE;
			
			this.fLineCount = 0;
			this.fCurrent = null;
			return comment;
		}
		
	}
	
	private final static RScannerPostExprVisitor POST_VISITOR = new RScannerPostExprVisitor();
	
	
	private final RLexer lexer;
	private final int level;
	
	private RTerminal nextType;
	private boolean wasLinebreak;
	
	private List<RAstNode> comments;
	private RoxygenCollector roxygen;
	private int commentsLevel;
	
	private final boolean createText;
	private final IStringFactory symbolTextFactory;
	
	
	public RScanner(final int level) {
		this(level, (IStringFactory) null);
	}
	
	public RScanner(final int level, final IStringFactory symbolTextFactory) {
		this(level, new RLexer((level == AstInfo.LEVEL_MINIMAL) ?
						(RLexer.DEFAULT | LEXER_CONFIG | RLexer.ENABLE_QUICK_CHECK) :
						(RLexer.DEFAULT | LEXER_CONFIG) ),
				symbolTextFactory );
	}
	
	public RScanner(final int level, final RLexer lexer, final IStringFactory symbolTextFactory) {
		if (lexer == null) {
			throw new NullPointerException("lexer"); //$NON-NLS-1$
		}
		this.symbolTextFactory = (symbolTextFactory != null) ? symbolTextFactory : StringFactory.INSTANCE;
		this.createText= ((level & AstInfo.DEFAULT_LEVEL_MASK) > AstInfo.LEVEL_MINIMAL);
		
		this.level= level;
		this.lexer= lexer;
	}
	
	
	public void setCommentLevel(final int level) {
		this.commentsLevel = level;
		if (level > 0) {
			this.comments = new ArrayList<>();
			if (level > 0x4) {
				this.roxygen = new RoxygenCollector();
			}
		}
	}
	
	public int getAstLevel() {
		return this.level;
	}
	
	public SourceComponent scanSourceUnit(final TextParserInput input) {
		try {
			this.lexer.reset(input);
			init();
			final SourceComponent rootNode= scanSourceUnit((RAstNode) null);
			return rootNode;
		}
		catch (final Exception e) {
			RCorePlugin.logError(-1, "Error occured while parsing R code", e);
			final SourceComponent dummy = new SourceComponent();
			dummy.fStatus = STATUS_RUNTIME_ERROR;
			return dummy;
		}
	}
	
	public SourceComponent scanSourceRange(final TextParserInput input, final IAstNode parent,
			final boolean expand) {
		final SourceComponent component= scanSourceRange(input, parent);
		if (expand) {
			component.fStartOffset= input.getStartIndex();
			component.fStopOffset= input.getStopIndex();
		}
		return component;
	}
	
	public SourceComponent scanSourceRange(final TextParserInput input, final IAstNode parent) {
		try {
			this.lexer.reset(input);
			init();
			final SourceComponent rootNode= scanSourceUnit((RAstNode) null);
			rootNode.fParent = parent;
			return rootNode;
		}
		catch (final Exception e) {
			RCorePlugin.logError(-1, "Error occured while parsing R code", e);
			final SourceComponent dummy = new SourceComponent();
			dummy.fStatus = STATUS_RUNTIME_ERROR;
			if (this.commentsLevel > 0) {
				dummy.fComments = Collections.emptyList();
			}
			return dummy;
		}
	}
	
	public RAstNode scanExpr(final TextParserInput input) {
		try {
			this.lexer.reset(input);
			init();
			final SourceComponent rootNode= scanSourceUnit((RAstNode) null);
			if (rootNode.getChildCount() == 1) {
				return rootNode.getChild(0);
			}
		}
		catch (final Exception e) {
			RCorePlugin.logError(-1, "Error occured while parsing R code", e);
		}
		return null;
	}
	
	public FDef scanFDef(final TextParserInput input) {
		try {
			this.lexer.reset(input);
			init();
			if (this.nextType == RTerminal.FUNCTION) {
				return scanFDef((ExprContext) null);
			}
		}
		catch (final Exception e) {
			RCorePlugin.logError(-1, "Error occured while parsing R code", e);
		}
		return null;
	}
	
	public FCall.Args scanFCallArgs(final TextParserInput input, final boolean expand) {
		try {
			this.lexer.reset(input);
			init();
			final FCall call= new FCall();
			call.fStopOffset= Integer.MIN_VALUE;
			scanInSpecArgs(call.fArgs);
			if (expand) {
				call.fArgs.fStartOffset= input.getStartIndex();
				call.fArgs.fStopOffset= input.getStopIndex();
			}
			return call.fArgs;
		}
		catch (final Exception e) {
			RCorePlugin.logError(-1, "Error occured while parsing R code", e);
		}
		return null;
	}
	
	private void init() {
		if (this.roxygen != null) {
			this.roxygen.init();
		}
		this.nextType = RTerminal.LINEBREAK;
		consumeToken();
	}
	
	final SourceComponent scanSourceUnit(final RAstNode parent) {
		final SourceComponent node = new SourceComponent();
		node.fRParent = parent;
		scanInExprList(node, true);
//		if (fNextType == RTerminal.EOF) {
//			fNext.type = null;
//		}
		if (this.commentsLevel > 0) {
			node.fComments = Collections.unmodifiableList(this.comments);
		}
		node.updateStartOffset();
		node.updateStopOffset();
		return node;
	}
	
	final void scanInExprList(final ExpressionList node, final boolean script) {
		ITER_TOKEN: while (true) {
			switch (this.nextType) {
			
			case EOF:
				break ITER_TOKEN;
				
			case LINEBREAK:
				consumeToken();
				continue ITER_TOKEN;
			
			default:
				{
					Expression expr = node.appendNewExpr();
					final ExprContext context = new ExprContext(node, expr,
							script ? LINE_MODE_CONSOLE : LINE_MODE_BLOCK );
					scanInExpression(context);
					
					if (expr.node == null) {
						node.fExpressions.remove(context.rootExpr);
						expr = null;
					}
					else {
						checkExpression(context);
					}
					switch (this.nextType) {
					
					case SEMI:
						if (expr != null) {
							node.setSeparator(this.lexer.getOffset());
							consumeToken();
							continue ITER_TOKEN;
						}
						// else error like comma
						//$FALL-THROUGH$
					case COMMA:
						{
							expr = node.appendNewExpr();
							expr.node = errorFromNext(node);
						}
						continue ITER_TOKEN;
						
					case SUB_INDEXED_CLOSE:
					case BLOCK_CLOSE:
					case GROUP_CLOSE:
						if (script) {
							expr = node.appendNewExpr();
							expr.node = errorFromNext(node);
							continue ITER_TOKEN;
						}
						break ITER_TOKEN;
					}
				}
			}
		}
	}
	
	final int scanInGroup(final RAstNode node, final Expression expr) {
		final ExprContext context = new ExprContext(node, expr, LINE_MODE_EAT);
		scanInExpression(context);
		return checkExpression(context);
	}
	
	final void scanInExpression(final ExprContext context) {
		this.wasLinebreak = false;
		ITER_TOKEN : while(true) {
			
			if (this.wasLinebreak && context.lineMode < LINE_MODE_EAT && context.openExpr == null) {
				break ITER_TOKEN;
			}
			
			switch (this.nextType) {
			
			case LINEBREAK:
				if (context.lineMode < LINE_MODE_EAT && context.openExpr == null) {
					break ITER_TOKEN;
				}
				consumeToken();
				continue ITER_TOKEN;
				
			case SYMBOL:
			case SYMBOL_G:
				if (this.wasLinebreak && context.openExpr == null) {
					break ITER_TOKEN;
				}
				appendNonOp(context, createSymbol(null));
				continue ITER_TOKEN;
			
			case TRUE:
			case FALSE:
			case NUM_NUM:
			case NUM_INT:
			case NUM_CPLX:
			case NA:
			case NA_REAL:
			case NA_INT:
			case NA_CPLX:
			case NA_CHAR:
			case NAN:
			case INF:
				if (this.wasLinebreak && context.openExpr == null) {
					break ITER_TOKEN;
				}
				appendNonOp(context, createNumberConst(null));
				continue ITER_TOKEN;
			case NULL:
				if (this.wasLinebreak && context.openExpr == null) {
					break ITER_TOKEN;
				}
				appendNonOp(context, createNullConst(null));
				continue ITER_TOKEN;
				
			case STRING_D:
			case STRING_S:
				if (this.wasLinebreak && context.openExpr == null) {
					break ITER_TOKEN;
				}
				appendNonOp(context, createStringConst(null));
				continue ITER_TOKEN;
				
			case ARROW_LEFT_S:
			case ARROW_LEFT_D:
			case ARROW_RIGHT_S:
			case ARROW_RIGHT_D:
			case EQUAL:
			case COLON_EQUAL:
				appendOp(context, createAssignment());
				continue ITER_TOKEN;
				
			case TILDE:
				if (context.openExpr != null) {
					appendNonOp(context, createModel());
				}
				else {
					appendOp(context, createModel());
				}
				continue ITER_TOKEN;
				
			case PLUS:
			case MINUS:
				if (context.openExpr != null) {
					appendNonOp(context, createSign());
				}
				else {
					appendOp(context, createArithmetic());
				}
				continue ITER_TOKEN;
			case MULT:
			case DIV:
				appendOp(context, createArithmetic());
				continue ITER_TOKEN;
			case POWER:
				appendOp(context, createPower());
				continue ITER_TOKEN;
				
			case SEQ:
				appendOp(context, createSeq());
				continue ITER_TOKEN;
				
			case SPECIAL:
				appendOp(context, createSpecial());
				continue ITER_TOKEN;
				
			case REL_LT:
			case REL_LE:
			case REL_EQ:
			case REL_GE:
			case REL_GT:
			case REL_NE:
				appendOp(context, createRelational());
				continue ITER_TOKEN;
				
			case OR:
			case OR_D:
			case AND:
			case AND_D:
				appendOp(context, createLogical());
				continue ITER_TOKEN;
				
			case NOT:
				if (this.wasLinebreak && context.openExpr == null) {
					break ITER_TOKEN;
				}
				appendNonOp(context, createSign());
				continue ITER_TOKEN;
			
			case IF:
				if (this.wasLinebreak && context.openExpr == null) {
					break ITER_TOKEN;
				}
				appendNonOp(context, scanCIf(context));
				continue ITER_TOKEN;
			case ELSE:
				if (context.rootNode.getNodeType() == NodeType.C_IF) {
					break ITER_TOKEN;
				}
				if (this.wasLinebreak && context.openExpr == null) {
					break ITER_TOKEN;
				}
				
				appendNonOp(context, scanCElse(context));
				continue ITER_TOKEN;
			
			case FOR:
				if (this.wasLinebreak && context.openExpr == null) {
					break ITER_TOKEN;
				}
				appendNonOp(context, scanCForLoop(context));
				continue ITER_TOKEN;
			case REPEAT:
				if (this.wasLinebreak && context.openExpr == null) {
					break ITER_TOKEN;
				}
				appendNonOp(context, scanCRepeatLoop(context));
				continue ITER_TOKEN;
			case WHILE:
				if (this.wasLinebreak && context.openExpr == null) {
					break ITER_TOKEN;
				}
				appendNonOp(context, scanCWhileLoop(context));
				continue ITER_TOKEN;
			
			case BREAK:
				if (this.wasLinebreak && context.openExpr == null) {
					break ITER_TOKEN;
				}
				appendNonOp(context, createLoopCommand());
				continue ITER_TOKEN;
			case NEXT:
				if (this.wasLinebreak && context.openExpr == null) {
					break ITER_TOKEN;
				}
				appendNonOp(context, createLoopCommand());
				continue ITER_TOKEN;
			
			case FUNCTION:
				if (this.wasLinebreak && context.openExpr == null) {
					break ITER_TOKEN;
				}
				appendNonOp(context, scanFDef(context));
				continue ITER_TOKEN;
				
			case GROUP_OPEN:
				if (context.openExpr != null) {
					appendNonOp(context, scanGroup());
				}
				else {
					appendOp(context, scanFCall());
				}
				continue ITER_TOKEN;
				
			case BLOCK_OPEN:
				if (this.wasLinebreak && context.openExpr == null) {
					break ITER_TOKEN;
				}
				appendNonOp(context, scanBlock());
				continue ITER_TOKEN;
			
			case EOF:
				break ITER_TOKEN;
				
			case NS_GET:
			case NS_GET_INT:
				if (this.wasLinebreak && context.openExpr == null) {
					break ITER_TOKEN;
				}
				appendNonOp(context, scanNSGet(context));
				continue ITER_TOKEN;
				
			case SUB_INDEXED_S_OPEN:
			case SUB_INDEXED_D_OPEN:
				appendOp(context, scanSubIndexed(context));
				continue ITER_TOKEN;
				
			case SUB_NAMED_PART:
			case SUB_NAMED_SLOT:
				appendOp(context, scanSubNamed(context));
				continue ITER_TOKEN;
				
			case QUESTIONMARK:
				if (context.openExpr != null) {
					appendNonOp(context, createHelp());
					continue ITER_TOKEN;
				}
				else {
					appendOp(context, createHelp());
					continue ITER_TOKEN;
				}
				
			case UNKNOWN:
			case IN:
				appendNonOp(context, errorFromNext(null));
				continue ITER_TOKEN;
				
			case COMMA:
			case SEMI:
			case SUB_INDEXED_CLOSE:
			case BLOCK_CLOSE:
			case GROUP_CLOSE:
				break ITER_TOKEN;
				
			default:
				throw new IllegalStateException("Unhandled token in expr-scanner: "+this.nextType.name());
			}
		}
	}
	
	final Group scanGroup() {
		final Group node = new Group();
		setupFromSourceToken(node);
		consumeToken();
		scanInGroup(node, node.fExpr);
		if (this.nextType == RTerminal.GROUP_CLOSE) {
			node.fGroupCloseOffset = this.lexer.getOffset();
			node.fStopOffset = node.fGroupCloseOffset+1;
			consumeToken();
			return node;
		}
		else {
			node.fStopOffset = this.lexer.getOffset();
			node.fStatus |= STATUS2_SYNTAX_CC_NOT_CLOSED;
			return node;
		}
	}
	
	final Block scanBlock() {
		final Block node = new Block();
		setupFromSourceToken(node);
		consumeToken();
		scanInExprList(node, false);
		if (this.nextType == RTerminal.BLOCK_CLOSE) {
			node.fBlockCloseOffset = this.lexer.getOffset();
			node.fStopOffset = node.fBlockCloseOffset+1;
			consumeToken();
			return node;
		}
		else {
			node.fStopOffset = this.lexer.getOffset();
			node.fStatus |= STATUS2_SYNTAX_CC_NOT_CLOSED;
			return node;
		}
	}
	
	final NSGet scanNSGet(final ExprContext context) {
		final NSGet node;
		switch (this.nextType) {
		case NS_GET:
			node = new NSGet.Std();
			break;
		case NS_GET_INT:
			node = new NSGet.Internal();
			break;
		default:
			throw new IllegalStateException();
		}
		setupFromSourceToken(node);
		node.fOperatorOffset = this.lexer.getOffset();
		consumeToken();
		
		// setup ns
		switch (context.lastNode.getNodeType()) {
		case SYMBOL:
		case STRING_CONST:
			{
				node.fNamespace = (SingleValue) context.lastNode;
				final RAstNode base = context.lastNode.fRParent;
				node.fNamespace.fRParent = node;
				final Expression expr = base.getExpr(node.fNamespace);
				if (expr != null) {
					expr.node = null;
				}
				else {
					throw new IllegalStateException(); // ?
				}
				context.update(base, expr);
				node.fStartOffset = node.fNamespace.fStartOffset;
				break;
			}
		default:
			node.fNamespace = errorNonExistingSymbol(node, node.fStartOffset, STATUS2_SYNTAX_ELEMENTNAME_MISSING);
			break;
		}
		
		// element
		switch (this.nextType) {
		case STRING_S:
		case STRING_D:
			node.fElement = createStringConst(node);
			node.fStopOffset = node.fElement.fStopOffset;
			return node;
		case SYMBOL:
		case SYMBOL_G:
			node.fElement = createSymbol(node);
			node.fStopOffset = node.fElement.fStopOffset;
			return node;
		default:
			node.fElement = errorNonExistingSymbol(node, node.fStopOffset, STATUS2_SYNTAX_ELEMENTNAME_MISSING);
			return node;
		}
	}
	
	final SubNamed scanSubNamed(final ExprContext context) {
		final SubNamed node;
		switch (this.nextType) {
		case SUB_NAMED_PART:
			node = new SubNamed.Named();
			break;
		case SUB_NAMED_SLOT:
			node = new SubNamed.Slot();
			break;
		default:
			throw new IllegalStateException();
		}
		setupFromSourceToken(node);
		node.fOperatorOffset = this.lexer.getOffset();
		consumeToken();
		readLines();
		
		switch (this.nextType) {
		case STRING_S:
		case STRING_D:
			node.fSubname = createStringConst(node);
			node.fStopOffset = node.fSubname.fStopOffset;
			return node;
		case SYMBOL:
		case SYMBOL_G:
			node.fSubname = createSymbol(node);
			node.fStopOffset = node.fSubname.fStopOffset;
			return node;
		default:
			node.fSubname = errorNonExistingSymbol(node, node.fStopOffset, STATUS2_SYNTAX_ELEMENTNAME_MISSING);
			return node;
		}
	}
	
	final SubIndexed scanSubIndexed(final ExprContext context) {
		final SubIndexed node;
		switch (this.nextType) {
		case SUB_INDEXED_S_OPEN:
			node = new SubIndexed.S();
			break;
		case SUB_INDEXED_D_OPEN:
			node = new SubIndexed.D();
			break;
		default:
			throw new IllegalStateException();
		}
		setupFromSourceToken(node);
		node.fOpenOffset = this.lexer.getOffset();
		consumeToken();
		readLines();
		
		scanInSpecArgs(node.fSublist);
		
		if (this.nextType == RTerminal.SUB_INDEXED_CLOSE) {
			node.fCloseOffset = this.lexer.getOffset();
			consumeToken();
			
			if (node.getNodeType() == NodeType.SUB_INDEXED_D) {
				if (this.nextType == RTerminal.SUB_INDEXED_CLOSE) {
					node.fClose2Offset = this.lexer.getOffset();
					node.fStopOffset = node.fClose2Offset+1;
					consumeToken();
					return node;
				}
				else {
					node.fStopOffset = node.fCloseOffset+1;
					node.fStatus |= STATUS2_SYNTAX_SUBINDEXED_NOT_CLOSED;
					return node;
				}
			}
			else {
				node.fStopOffset = node.fCloseOffset+1;
				return node;
			}
		}
		else {
			node.fStopOffset = node.fSublist.fStopOffset;
			node.fStatus |= STATUS2_SYNTAX_SUBINDEXED_NOT_CLOSED;
			return node;
		}
	}
	
	final CIfElse scanCIf(final ExprContext context) {
		final CIfElse node = new CIfElse();
		setupFromSourceToken(node);
		consumeToken();
		int ok = 0;
		readLines();
		
		if (this.nextType == RTerminal.GROUP_OPEN) {
			node.fCondOpenOffset = this.lexer.getOffset();
			node.fStopOffset = this.lexer.getOffset()+1;
			consumeToken();
			readLines();
			
			// condition
			ok += scanInGroup(node, node.fCondExpr);
			
			if (this.nextType == RTerminal.GROUP_CLOSE) {
				node.fCondCloseOffset = this.lexer.getOffset();
				node.fStopOffset = node.fCondCloseOffset+1;
				consumeToken();
				ok = 1;
				readLines();
			}
			else {
				node.fStopOffset = node.fCondExpr.node.fStopOffset;
				node.fStatus |= STATUS2_SYNTAX_CONDITION_NOT_CLOSED;
			}
		}
		else {
			node.fStatus = STATUS2_SYNTAX_CONDITION_MISSING;
			node.fCondExpr.node = errorNonExistExpression(node, node.fStopOffset,
					(STATUS2_SYNTAX_EXPR_AS_CONDITION_MISSING | STATUSFLAG_SUBSEQUENT | STATUS3_IF));
		}
		
		// then
		if (ok > 0 || recoverCCont()) {
			final ExprContext thenContext = new ExprContext(node, node.fThenExpr, context.lineMode);
			scanInExpression(thenContext);
			checkExpression(thenContext);
			node.fStopOffset = node.fThenExpr.node.fStopOffset;
			if (context.lineMode >= LINE_MODE_BLOCK) {
				readLines();
			}
		}
		else {
			node.fThenExpr.node = errorNonExistExpression(node, node.fCondExpr.node.fStopOffset,
					(STATUS2_SYNTAX_EXPR_AS_BODY_MISSING | STATUSFLAG_SUBSEQUENT | STATUS3_IF));
		}
		
		// else
		if (this.nextType == RTerminal.ELSE) {
			node.fWithElse = true;
			node.fElseOffset = this.lexer.getOffset();
			consumeToken();
			// else body is added via common expression processing
		}
		
		return node;
	}
	
	final CIfElse scanCElse(final ExprContext context) { // else without if
		final CIfElse node = new CIfElse();
		setupFromSourceToken(node);
		node.fStatus = STATUS2_SYNTAX_IF_MISSING;
		node.fCondExpr.node = errorNonExistExpression(node, node.fStartOffset,
				(STATUS2_SYNTAX_EXPR_AS_CONDITION_MISSING | STATUSFLAG_SUBSEQUENT | STATUS3_IF));
		node.fThenExpr.node = errorNonExistExpression(node, node.fStartOffset,
				(STATUS2_SYNTAX_EXPR_AS_BODY_MISSING | STATUSFLAG_SUBSEQUENT | STATUS3_IF));
		node.fElseOffset = this.lexer.getOffset();
		node.fWithElse = true;
		consumeToken();
		
		return node;
	}
	
	final CForLoop scanCForLoop(final ExprContext context) {
		final CForLoop node = new CForLoop();
		setupFromSourceToken(node);
		consumeToken();
		int ok = 0;
		readLines();
		
		if (this.nextType == RTerminal.GROUP_OPEN) {
			node.fCondOpenOffset = this.lexer.getOffset();
			consumeToken();
			readLines();
			
			// condition
			switch (this.nextType) {
			case SYMBOL:
			case SYMBOL_G:
				node.fVarSymbol = createSymbol(node);
				readLines();
				break;
			default:
				node.fVarSymbol = errorNonExistingSymbol(node, node.fCondOpenOffset+1, STATUS2_SYNTAX_SYMBOL_MISSING);
				ok--;
				break;
			}
			
			if (this.nextType == RTerminal.IN) {
				node.fInOffset = this.lexer.getOffset();
				node.fStopOffset = node.fInOffset+2;
				consumeToken();
				readLines();
				
				ok+= scanInGroup(node, node.fCondExpr);
			}
			else {
				node.fStopOffset = node.fVarSymbol.fStopOffset;
				node.fStatus |= (ok >= 0) ? STATUS2_SYNTAX_IN_MISSING :
						(STATUS2_SYNTAX_IN_MISSING | STATUSFLAG_SUBSEQUENT);
				node.fCondExpr.node = errorNonExistExpression(node, node.fStopOffset,
						(STATUS2_SYNTAX_EXPR_AS_CONDITION_MISSING | STATUSFLAG_SUBSEQUENT));
			}
			
			if (this.nextType == RTerminal.GROUP_CLOSE) {
				node.fCondCloseOffset = this.lexer.getOffset();
				node.fStopOffset = node.fCondCloseOffset+1;
				consumeToken();
				ok = 1;
				readLines();
			}
			else {
				node.fStopOffset = node.fCondExpr.node.fStopOffset;
				if ((node.fStatus & STATUSFLAG_REAL_ERROR) == 0) {
					node.fStatus |= (ok >= 0) ? STATUS2_SYNTAX_CONDITION_NOT_CLOSED :
							(STATUS2_SYNTAX_CONDITION_NOT_CLOSED | STATUSFLAG_SUBSEQUENT);
				}
			}
		}
		else { // missing GROUP_OPEN
			node.fStatus = STATUS2_SYNTAX_CONDITION_MISSING;
			node.fVarSymbol = errorNonExistingSymbol(node, node.fStopOffset,
					STATUS2_SYNTAX_SYMBOL_MISSING | STATUSFLAG_SUBSEQUENT);
			node.fCondExpr.node = errorNonExistExpression(node, node.fStopOffset,
					(STATUS2_SYNTAX_EXPR_AS_CONDITION_MISSING | STATUSFLAG_SUBSEQUENT | STATUS3_FOR));
		}
		
		// loop
		if (ok <= 0 && !recoverCCont()) {
			node.fLoopExpr.node = errorNonExistExpression(node, node.fStopOffset,
					(STATUS2_SYNTAX_EXPR_AS_BODY_MISSING | STATUSFLAG_SUBSEQUENT | STATUS3_FOR));
		}
		
		return node;
	}
	
	final CWhileLoop scanCWhileLoop(final ExprContext context) {
		final CWhileLoop node = new CWhileLoop();
		setupFromSourceToken(node);
		consumeToken();
		int ok = 0;
		readLines();
		
		if (this.nextType == RTerminal.GROUP_OPEN) {
			node.fCondOpenOffset = this.lexer.getOffset();
			node.fStopOffset = node.fCondOpenOffset+1;
			consumeToken();
			readLines();
			
			// condition
			ok += scanInGroup(node, node.fCondExpr);
			
			if (this.nextType == RTerminal.GROUP_CLOSE) {
				node.fCondCloseOffset = this.lexer.getOffset();
				node.fStopOffset = node.fCondCloseOffset+1;
				consumeToken();
				ok = 1;
				readLines();
			}
			else {
				node.fStopOffset = node.fCondExpr.node.fStopOffset;
				node.fStatus = (ok >= 0) ? STATUS2_SYNTAX_CONDITION_NOT_CLOSED :
						(STATUS2_SYNTAX_CONDITION_NOT_CLOSED | STATUSFLAG_SUBSEQUENT);
			}
		}
		else {
			node.fStatus = STATUS2_SYNTAX_CONDITION_MISSING;
			node.fCondExpr.node = errorNonExistExpression(node, node.fStopOffset,
					(STATUS2_SYNTAX_EXPR_AS_CONDITION_MISSING | STATUSFLAG_SUBSEQUENT | STATUS3_WHILE));
		}
		
		// loop
		if (ok <= 0 && !recoverCCont()) {
			node.fLoopExpr.node = errorNonExistExpression(node, node.fStopOffset,
					(STATUS2_SYNTAX_EXPR_AS_BODY_MISSING | STATUSFLAG_SUBSEQUENT | STATUS3_WHILE));
		}
		
		return node;
	}
	
	final CRepeatLoop scanCRepeatLoop(final ExprContext context) {
		final CRepeatLoop node = new CRepeatLoop();
		setupFromSourceToken(node);
		consumeToken();
		
		return node;
	}
	
	final FDef scanFDef(final ExprContext context) {
		final FDef node = new FDef();
		setupFromSourceToken(node);
		consumeToken();
		int ok = 0;
		readLines();
		
		if (this.nextType == RTerminal.GROUP_OPEN) {
			node.fArgsOpenOffset = this.lexer.getOffset();
			node.fStopOffset = node.fArgsOpenOffset+1;
			consumeToken();
			readLines();
			
			// args
			scanInFDefArgs(node.fArgs);
			
			if (this.nextType == RTerminal.GROUP_CLOSE) {
				node.fArgsCloseOffset = this.lexer.getOffset();
				node.fStopOffset = node.fArgsCloseOffset+1;
				consumeToken();
				ok = 1;
				readLines();
			}
			else {
				node.fStopOffset = node.fArgs.fStopOffset;
				node.fStatus |= STATUS2_SYNTAX_FDEF_ARGS_NOT_CLOSED;
			}
		}
		else {
			node.fArgs.fStartOffset = node.fArgs.fStopOffset = node.fStopOffset;
			node.fStatus = STATUS2_SYNTAX_FDEF_ARGS_MISSING;
		}
		
		// body
		if (ok <= 0 && !recoverCCont()) {
			node.fExpr.node = errorNonExistExpression(node, node.fStopOffset,
					(STATUS2_SYNTAX_EXPR_AS_BODY_MISSING | STATUSFLAG_SUBSEQUENT | STATUS3_FDEF));
		}
		
		return node;
	}
	
	final FCall scanFCall() {
		final FCall node = new FCall();
		
		setupFromSourceToken(node);
		node.fArgsOpenOffset = this.lexer.getOffset();
		consumeToken();
		readLines();
		
		scanInSpecArgs(node.fArgs);
		
		if (this.nextType == RTerminal.GROUP_CLOSE) {
			node.fArgsCloseOffset = this.lexer.getOffset();
			node.fStopOffset = node.fArgsCloseOffset+1;
			consumeToken();
		}
		else {
			node.fStopOffset = node.fArgs.fStopOffset;
			node.fStatus |= STATUS2_SYNTAX_FCALL_NOT_CLOSED;
		}
		
		return node;
	}
	
	final void scanInFDefArgs(final FDef.Args args) {
		args.fStartOffset = args.fStopOffset = args.fRParent.fStopOffset;
		ITER_ARGS : while (true) {
			final FDef.Arg arg = new FDef.Arg(args);
			switch(this.nextType) {
			case SYMBOL:
			case SYMBOL_G:
				arg.fArgName = createSymbol(arg);
				arg.fStartOffset = arg.fArgName.fStartOffset;
				arg.fStopOffset = arg.fArgName.fStopOffset;
				readLines();
				break;
			case EQUAL:
			case COMMA:
				arg.fStartOffset = arg.fStopOffset = this.lexer.getOffset();
				break;
			default:
				if (args.fSpecs.isEmpty()) {
					return;
				}
				arg.fStartOffset = arg.fStopOffset = args.fStopOffset;
				break;
			}
			
			if (arg.fArgName == null) {
				arg.fArgName = errorNonExistingSymbol(arg, arg.fStopOffset, STATUS2_SYNTAX_SYMBOL_MISSING);
			}
			
			if (this.nextType == RTerminal.EQUAL) {
				arg.fStopOffset = this.lexer.getOffset()+1;
				consumeToken();
				
				final Expression expr = arg.addDefault();
				scanInGroup(arg, expr);
				arg.fStopOffset = arg.fDefaultExpr.node.fStopOffset;
			}
			
			args.fSpecs.add(arg);
			args.fStatus = POST_VISITOR.checkTerminal(arg);
			if (this.nextType == RTerminal.COMMA) {
				args.fStopOffset = this.lexer.getOffset()+1;
				consumeToken();
				readLines();
				continue ITER_ARGS;
			}
			else {
				args.fStartOffset = args.fSpecs.get(0).fStartOffset;
				args.fStopOffset = arg.fStopOffset;
				return;
			}
		}
	}
	
	final void scanInSpecArgs(final FCall.Args args) {
		args.fStartOffset = args.fStopOffset = args.fRParent.fStopOffset;
		ITER_ARGS : while (true) {
			final FCall.Arg arg = new FCall.Arg(args);
			arg.fStartOffset = this.lexer.getOffset();
			switch(this.nextType) {
			case SYMBOL:
				arg.fArgName = createSymbol(arg);
				readLines();
				break;
			case STRING_S:
			case STRING_D:
				arg.fArgName = createStringConst(arg);
				readLines();
				break;
			case NULL:
				arg.fArgName = createNullConst(arg);
				readLines();
				break;
			case EQUAL:
				arg.fArgName = errorNonExistingSymbol(arg, this.lexer.getOffset(), STATUS2_SYNTAX_ELEMENTNAME_MISSING);
				break;
			default:
				break;
			}
			if (arg.fArgName != null) {
				if (this.nextType == RTerminal.EQUAL) {
					arg.fEqualsOffset = this.lexer.getOffset();
					arg.fStopOffset = arg.fEqualsOffset+1;
					consumeToken();
					
					final ExprContext valueContext = new ExprContext(arg, arg.fValueExpr, LINE_MODE_EAT);
					scanInExpression(valueContext);
					if (arg.fValueExpr.node != null) { // empty items are allowed
						checkExpression(valueContext);
						arg.fStopOffset = arg.fValueExpr.node.fStopOffset;
					}
				}
				else {
					// argName -> valueExpr
					arg.fValueExpr.node = arg.fArgName;
					arg.fArgName = null;
					
					final ExprContext valueContext = new ExprContext(arg, arg.fValueExpr, LINE_MODE_EAT);
					valueContext.update(arg.fValueExpr.node, null);
					scanInExpression(valueContext);
					checkExpression(valueContext);
					arg.fStopOffset = arg.fValueExpr.node.fStopOffset;
				}
			}
			else {
				final ExprContext valueContext = new ExprContext(arg, arg.fValueExpr, LINE_MODE_EAT);
				scanInExpression(valueContext);
				if (arg.fValueExpr.node != null) { // empty items are allowed
					checkExpression(valueContext);
					arg.fStopOffset = arg.fValueExpr.node.fStopOffset;
				}
				else {
					arg.fStartOffset = arg.fStopOffset = args.fStopOffset;
				}
			}
			
			if (this.nextType == RTerminal.COMMA) {
				args.fSpecs.add(arg);
				args.fStatus = POST_VISITOR.checkTerminal(arg);
				args.fSepList.add(this.lexer.getOffset());
				args.fStopOffset = this.lexer.getOffset()+1;
				consumeToken();
				readLines();
				continue ITER_ARGS;
			}
			// last arg before )
			if (args.fSpecs.isEmpty() && !arg.hasChildren()) {
				return;
			}
			args.fSpecs.add(arg);
			args.fStatus = POST_VISITOR.checkTerminal(arg);
			args.fStartOffset = args.fSpecs.get(0).fStartOffset;
			args.fStopOffset = arg.fStopOffset;
			return;
		}
	}
	
	final void scanInSpecArgs(final SubIndexed.Args args) {
		args.fStartOffset = args.fStopOffset = args.fRParent.fStopOffset;
		ITER_ARGS : while (true) {
			final SubIndexed.Arg arg = new SubIndexed.Arg(args);
			arg.fStartOffset = this.lexer.getOffset();
			switch(this.nextType) {
			case SYMBOL:
				arg.fArgName = createSymbol(arg);
				readLines();
				break;
			case STRING_S:
			case STRING_D:
				arg.fArgName = createStringConst(arg);
				readLines();
				break;
			case NULL:
				arg.fArgName = createNullConst(arg);
				readLines();
				break;
			case EQUAL:
				arg.fArgName = errorNonExistingSymbol(arg, this.lexer.getOffset(), STATUS2_SYNTAX_ELEMENTNAME_MISSING);
				break;
			default:
				break;
			}
			if (arg.fArgName != null) {
				if (this.nextType == RTerminal.EQUAL) {
					arg.fEqualsOffset = this.lexer.getOffset();
					arg.fStopOffset = arg.fEqualsOffset+1;
					consumeToken();
					
					final ExprContext valueContext = new ExprContext(arg, arg.fValueExpr, LINE_MODE_EAT);
					scanInExpression(valueContext);
					if (arg.fValueExpr.node != null) { // empty items are allowed
						checkExpression(valueContext);
						arg.fStopOffset = arg.fValueExpr.node.fStopOffset;
					}
				}
				else {
					// argName -> valueExpr
					arg.fValueExpr.node = arg.fArgName;
					arg.fArgName = null;
					
					final ExprContext valueContext = new ExprContext(arg, arg.fValueExpr, LINE_MODE_EAT);
					valueContext.update(arg.fValueExpr.node, null);
					scanInExpression(valueContext);
					checkExpression(valueContext);
					arg.fStopOffset = arg.fValueExpr.node.fStopOffset;
				}
			}
			else {
				final ExprContext valueContext = new ExprContext(arg, arg.fValueExpr, LINE_MODE_EAT);
				scanInExpression(valueContext);
				if (arg.fValueExpr.node != null) { // empty items are allowed
					checkExpression(valueContext);
					arg.fStopOffset = arg.fValueExpr.node.fStopOffset;
				}
				else {
					arg.fStartOffset = arg.fStopOffset = args.fStopOffset;
				}
			}
			
			if (this.nextType == RTerminal.COMMA) {
				args.fSpecs.add(arg);
				args.fStatus = POST_VISITOR.checkTerminal(arg);
				args.fStopOffset = this.lexer.getOffset()+1;
				consumeToken();
				readLines();
				continue ITER_ARGS;
			}
			// last arg before )
			if (args.fSpecs.isEmpty() && !arg.hasChildren()) {
				return;
			}
			args.fSpecs.add(arg);
			args.fStatus = POST_VISITOR.checkTerminal(arg);
			args.fStartOffset = args.fSpecs.get(0).fStartOffset;
			args.fStopOffset = arg.fStopOffset;
			return;
		}
	}
	
	final boolean recoverCCont() {
		return !this.wasLinebreak
			&& (this.nextType == RTerminal.SYMBOL || this.nextType == RTerminal.SYMBOL_G || this.nextType == RTerminal.BLOCK_OPEN);
	}
	
	final void appendNonOp(final ExprContext context, final RAstNode newNode) {
		if (context.openExpr != null) {
			newNode.fRParent = context.lastNode;
			context.openExpr.node = newNode;
		}
		else {
			// setup missing op
			final Dummy.Operator error = new Dummy.Operator(STATUS2_SYNTAX_OPERATOR_MISSING);
			error.fRParent = context.rootNode;
			error.fLeftExpr.node = context.rootExpr.node;
			error.fStartOffset = error.fStopOffset = newNode.fStartOffset;
			context.rootExpr.node = error;
			// append news
			newNode.fRParent = error;
			error.fRightExpr.node = newNode;
			context.rootExpr.node = error;
		}
		context.update(newNode, newNode.getRightExpr());
		return;
	}
	
	final void appendOp(final ExprContext context, final RAstNode newNode) {
		if (context.openExpr != null) {
			context.openExpr.node = errorNonExistExpression(context.lastNode, newNode.fStartOffset, STATUS2_SYNTAX_EXPR_BEFORE_OP_MISSING);
			context.update(context.openExpr.node, null);
		}
		
		final int newP = newNode.getNodeType().opPrec;
		RAstNode left = context.lastNode;
		RAstNode cand = context.lastNode;
		
		ITER_CAND : while (cand != null && cand != context.rootNode) {
			final NodeType candType = cand.getNodeType();
			if (candType.opPrec == newP) {
				switch (candType.opAssoc) {
				case Assoc.NOSTD:
					left = cand;
					if ((newNode.fStatus & STATUSFLAG_REAL_ERROR) == 0) {
						newNode.fStatus = STATUS123_SYNTAX_SEQREL_UNEXPECTED;
					}
					break ITER_CAND;
				case Assoc.LEFTSTD:
					left = cand;
					break ITER_CAND;
				case Assoc.RIGHTSTD:
				default:
					break ITER_CAND;
				}
			}
			if (candType.opPrec > newP) {
				break ITER_CAND;
			}
			left = cand;
			cand = cand.fRParent;
		}
		
		final RAstNode baseNode = left.fRParent;
		if (baseNode == null) {
			throw new IllegalStateException(); // DEBUG
		}
		final Expression baseExpr = baseNode.getExpr(left);
		newNode.getLeftExpr().node = left;
		left.fRParent = newNode;
		baseExpr.node = newNode;
		newNode.fRParent = baseNode;
		
		context.update(newNode, newNode.getRightExpr());
		return;
	}
	
	Dummy.Terminal errorNonExistExpression(final RAstNode parent, final int stopHint, final int status) {
		final Dummy.Terminal error = new Dummy.Terminal(status);
		error.fRParent = parent;
		error.fStartOffset = error.fStopOffset = (stopHint != Integer.MIN_VALUE) ? stopHint : parent.fStopOffset;
		error.fText = ""; //$NON-NLS-1$
		parent.fStatus |= STATUSFLAG_ERROR_IN_CHILD;
		return error;
	}
	
	Dummy.Terminal errorFromNext(final RAstNode parent) {
		final Dummy.Terminal error = new Dummy.Terminal((this.nextType == RTerminal.UNKNOWN) ?
				STATUS12_SYNTAX_TOKEN_UNKNOWN : STATUS12_SYNTAX_TOKEN_UNEXPECTED);
		error.fRParent = parent;
		error.fStartOffset = this.lexer.getOffset();
		error.fStopOffset = this.lexer.getOffset()+this.lexer.getLength();
		if (this.createText) {
			error.fText = this.lexer.getText();
		}
		consumeToken();
		if (parent != null) {
			parent.fStatus |= STATUSFLAG_ERROR_IN_CHILD;
		}
		return error;
	}
	
	Symbol errorNonExistingSymbol(final RAstNode parent, final int offset, final int status) {
		final Symbol error = new Symbol.Std();
		error.fRParent = parent;
		error.fStartOffset = error.fStopOffset = offset;
		error.fText = ""; //$NON-NLS-1$
		error.fStatus = status;
		parent.fStatus |= STATUSFLAG_ERROR_IN_CHILD;
		return error;
	}
	
	protected Symbol createSymbol(final RAstNode parent) {
		final Symbol symbol;
		switch (this.nextType) {
		case SYMBOL_G:
			symbol = new Symbol.G();
			break;
		case SYMBOL:
			symbol = new Symbol.Std();
			break;
		default:
			throw new IllegalStateException();
		}
		symbol.fRParent = parent;
		setupFromSourceToken(symbol);
		if (parent != null) {
			parent.fStatus |= POST_VISITOR.checkTerminal(symbol);
		}
		consumeToken();
		return symbol;
	}
	
	protected NumberConst createNumberConst(final RAstNode parent) {
		final NumberConst num = new NumberConst(this.nextType);
		num.fRParent = parent;
		setupFromSourceToken(num);
		consumeToken();
		return num;
	}
	
	protected NullConst createNullConst(final RAstNode parent) {
		final NullConst num = new NullConst();
		num.fRParent = parent;
		setupFromSourceToken(num);
		consumeToken();
		return num;
	}
	
	protected StringConst createStringConst(final RAstNode parent) {
		final StringConst str;
		switch (this.nextType) {
		case STRING_D:
			str = new StringConst.D();
			break;
		case STRING_S:
			str = new StringConst.S();
			break;
		default:
			throw new IllegalStateException();
		}
		str.fRParent = parent;
		setupFromSourceToken(str);
		consumeToken();
		return str;
	}
	
	protected Assignment createAssignment() {
		Assignment node;
		switch (this.nextType) {
		case ARROW_LEFT_S:
			node = new Assignment.LeftS();
			break;
		case ARROW_LEFT_D:
			node = new Assignment.LeftD();
			break;
		case ARROW_RIGHT_S:
			node = new Assignment.RightS();
			break;
		case ARROW_RIGHT_D:
			node = new Assignment.RightD();
			break;
		case EQUAL:
			node = new Assignment.LeftE();
			break;
		case COLON_EQUAL:
			node = new Assignment.LeftC();
			break;
		default:
			throw new IllegalStateException();
		}
		setupFromSourceToken(node);
		consumeToken();
		return node;
	}
	
	protected Model createModel() {
		final Model node = new Model();
		setupFromSourceToken(node);
		consumeToken();
		return node;
	}
	
	protected CLoopCommand createLoopCommand() {
		final CLoopCommand node;
		switch (this.nextType) {
		case NEXT:
			node = new CLoopCommand.Next();
			break;
		case BREAK:
			node = new CLoopCommand.Break();
			break;
		default:
			throw new IllegalStateException();
		}
		setupFromSourceToken(node);
		consumeToken();
		return node;
	}
	
	protected Sign createSign() {
		final Sign node;
		switch (this.nextType) {
		case PLUS:
			node = new Sign.PlusSign();
			break;
		case MINUS:
			node = new Sign.MinusSign();
			break;
		case NOT:
			node = new Sign.Not();
			break;
		default:
			throw new IllegalStateException();
		}
		setupFromSourceToken(node);
		consumeToken();
		return node;
	}
	
	protected Arithmetic createArithmetic() {
		final Arithmetic node;
		switch (this.nextType) {
		case PLUS:
			node = new Arithmetic.Plus();
			break;
		case MINUS:
			node = new Arithmetic.Minus();
			break;
		case MULT:
			node = new Arithmetic.Mult();
			break;
		case DIV:
			node = new Arithmetic.Div();
			break;
		default:
			throw new IllegalStateException();
		}
		setupFromSourceToken(node);
		consumeToken();
		return node;
	}
	
	protected Power createPower() {
		final Power node = new Power();
		setupFromSourceToken(node);
		consumeToken();
		return node;
	}
	
	protected Seq createSeq() {
		final Seq node = new Seq();
		setupFromSourceToken(node);
		consumeToken();
		return node;
	}
	
	protected Special createSpecial() {
		final Special node = new Special();
		setupFromSourceToken(node);
		if (this.createText) {
			node.fQualifier = this.lexer.getText(this.symbolTextFactory);
		}
		consumeToken();
		return node;
	}
	
	protected Relational createRelational() {
		final Relational node;
		switch (this.nextType) {
		case REL_LT:
			node = new Relational.LT();
			break;
		case REL_LE:
			node = new Relational.LE();
			break;
		case REL_EQ:
			node = new Relational.EQ();
			break;
		case REL_GE:
			node = new Relational.GE();
			break;
		case REL_GT:
			node = new Relational.GT();
			break;
		case REL_NE:
			node = new Relational.NE();
			break;
		default:
			throw new IllegalStateException();
		}
		setupFromSourceToken(node);
		consumeToken();
		return node;
	}
	
	protected Logical createLogical() {
		final Logical node;
		switch (this.nextType) {
		case AND:
			node = new Logical.And();
			break;
		case AND_D:
			node = new Logical.AndD();
			break;
		case OR:
			node = new Logical.Or();
			break;
		case OR_D:
			node = new Logical.OrD();
			break;
		default:
			throw new IllegalStateException();
		}
		setupFromSourceToken(node);
		consumeToken();
		return node;
	}
	
	protected Help createHelp() {
		final Help node = new Help();
		setupFromSourceToken(node);
		consumeToken();
		return node;
	}
	
	private final void setupFromSourceToken(final RAstNode node) {
		node.fStartOffset = this.lexer.getOffset();
		node.fStopOffset = this.lexer.getOffset()+this.lexer.getLength();
		node.fStatus = this.lexer.getFlags();
	}
	
	private final void setupFromSourceToken(final Symbol node) {
		node.fStartOffset = this.lexer.getOffset();
		node.fStopOffset = this.lexer.getOffset()+this.lexer.getLength();
		if (this.createText) {
			node.fText = this.lexer.getText(this.symbolTextFactory);
			if (this.lexer.getStatusDetail() != null) {
				node.addAttachment(this.lexer.getStatusDetail());
			}
		}
		node.fStatus = this.lexer.getFlags();
	}
	
	private final void setupFromSourceToken(final SingleValue node) {
		node.fStartOffset = this.lexer.getOffset();
		node.fStopOffset = this.lexer.getOffset()+this.lexer.getLength();
		if (this.createText) {
			node.fText = this.lexer.getText();
			if (this.lexer.getStatusDetail() != null) {
				node.addAttachment(this.lexer.getStatusDetail());
			}
		}
		node.fStatus = this.lexer.getFlags();
	}
	
	private final int checkExpression(final ExprContext context) {
		int state = 0;
		if (context.openExpr != null && context.openExpr.node == null) {
			context.openExpr.node = errorNonExistExpression(context.lastNode, context.lastNode.fStopOffset,
					context.lastNode.getMissingExprStatus(context.openExpr));
			state = -1;
		}
		context.rootNode.fStatus |= POST_VISITOR.check(context.rootExpr.node);
		return state;
	}
	
	private final void readLines() {
		while (this.nextType == RTerminal.LINEBREAK) {
			consumeToken();
		}
	}
	
	private final void consumeToken() {
		this.wasLinebreak = (this.nextType == RTerminal.LINEBREAK);
		this.nextType = this.lexer.next();
		switch (this.nextType) {
		case COMMENT:
		case ROXYGEN_COMMENT:
			if (this.commentsLevel > 0x4) {
				consumeCommentWithRoxygen();
			}
			else {
				consumeComment();
			}
			return;
		default:
			return;
		}
	}
	
	
	private void consumeCommentWithRoxygen() {
		while (true) {
			final Comment comment;
			switch (this.nextType) {
			case COMMENT:
				if (this.roxygen.hasComment()) {
					this.comments.add(this.roxygen.finish(this.lexer));
				}
				comment = new Comment.CommonLine();
				setupFromSourceToken(comment);
				this.comments.add(comment);
				
				this.nextType = this.lexer.next();
				continue;
				
			case ROXYGEN_COMMENT:
				comment = new Comment.RoxygenLine();
				setupFromSourceToken(comment);
				this.roxygen.add(comment);
				
				this.nextType = this.lexer.next();
				continue;
				
			case LINEBREAK:
				this.nextType = this.lexer.next();
				if (this.nextType == RTerminal.LINEBREAK && this.roxygen.hasComment()) {
					this.comments.add(this.roxygen.finish(null));
				}
				continue;
				
			default:
				if (this.roxygen.hasComment()) {
					this.comments.add(this.roxygen.finish(this.lexer));
				}
				
				this.wasLinebreak = true;
				return;
			}
		}
	}
	
	private void consumeComment() {
		while (true) {
			switch (this.nextType) {
			case COMMENT:
			case ROXYGEN_COMMENT:
				if (this.commentsLevel > 0) {
					final Comment comment = (this.nextType == RTerminal.ROXYGEN_COMMENT) ?
							new Comment.RoxygenLine() :
							new Comment.CommonLine();
					setupFromSourceToken(comment);
					this.comments.add(comment);
				} // no break
				
				this.nextType = this.lexer.next();
				continue;
				
			case LINEBREAK:
				this.nextType = this.lexer.next();
				continue;
				
			default:
				this.wasLinebreak = true;
				return;
			}
		}
	}
	
}
