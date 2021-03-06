/*=============================================================================#
 # Copyright (c) 2008-2015 Stephan Wahlbrink (WalWare.de) and others.
 # All rights reserved. This program and the accompanying materials
 # are made available under the terms of the Eclipse Public License v1.0
 # which accompanies this distribution, and is available at
 # http://www.eclipse.org/legal/epl-v10.html
 # 
 # Contributors:
 #     Stephan Wahlbrink - initial API and implementation
 #=============================================================================*/

package de.walware.statet.r.core.rsource;


public interface IRSourceConstants {
	
	public static final int STATUS_MASK_1=                  0x0_0000_ff00;
	public static final int STATUS_MASK_12=                 0x0_000f_fff0;
	public static final int STATUS_MASK_3=                  0x0_0000_000f;
	public static final int STATUS_MASK_123=                0x0_000f_ffff;
	
	public static final int STATUS_OK=                      0x0_0000_0000;
	public static final int STATUS_RUNTIME_ERROR=           0x0_0000_f000;
	public static final int STATUSFLAG_REAL_ERROR=          0x0_0001_0000;
	public static final int STATUSFLAG_SUBSEQUENT=          0x0_0010_0000;
	public static final int STATUSFLAG_ERROR_IN_CHILD=      0x0_0100_0000;
	
	/**
	 * An existing token is not OK.
	 */
	public static final int STATUS1_SYNTAX_INCORRECT_TOKEN=                     0x1100;
	public static final int STATUS12_SYNTAX_TOKEN_NOT_CLOSED=                   0x1110 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS12_SYNTAX_NUMBER_INVALID=                     0x1130 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS123_SYNTAX_NUMBER_HEX_DIGIT_MISSING=          STATUS12_SYNTAX_NUMBER_INVALID | 5;
	public static final int STATUS123_SYNTAX_NUMBER_HEX_FLOAT_EXP_MISSING=      STATUS12_SYNTAX_NUMBER_INVALID | 6;
	public static final int STATUS123_SYNTAX_NUMBER_EXP_DIGIT_MISSING=          STATUS12_SYNTAX_NUMBER_INVALID | 7;
	public static final int STATUS12_SYNTAX_NUMBER_MISLEADING=                  0x1140;
	public static final int STATUS123_SYNTAX_NUMBER_NON_INT_WITH_L=             STATUS12_SYNTAX_NUMBER_MISLEADING | 1;
	public static final int STATUS123_SYNTAX_NUMBER_INT_WITH_DEC_POINT=         STATUS12_SYNTAX_NUMBER_MISLEADING | 2;
	public static final int STATUS12_SYNTAX_TEXT_INVALID=                       0x1150 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS123_SYNTAX_TEXT_NULLCHAR=                     STATUS12_SYNTAX_TEXT_INVALID | 1;
	public static final int STATUS123_SYNTAX_TEXT_ESCAPE_SEQ_UNEXPECTED=        STATUS12_SYNTAX_TEXT_INVALID | 3;
	public static final int STATUS123_SYNTAX_TEXT_ESCAPE_SEQ_NOT_CLOSED=        STATUS12_SYNTAX_TEXT_INVALID | 4;
	public static final int STATUS123_SYNTAX_TEXT_ESCAPE_SEQ_HEX_DIGIT_MISSING= STATUS12_SYNTAX_TEXT_INVALID | 5;
	public static final int STATUS123_SYNTAX_TEXT_ESCAPE_SEQ_UNKOWN=            STATUS12_SYNTAX_TEXT_INVALID | 9;
	public static final int STATUS123_SYNTAX_TEXT_ESCAPE_SEQ_CODEPOINT_INVALID= 0x1160;
	public static final int STATUS12_SYNTAX_TOKEN_UNKNOWN=                      0x1190 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS12_SYNTAX_TOKEN_UNEXPECTED=                   0x11A0 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS123_SYNTAX_SEQREL_UNEXPECTED=                 STATUS12_SYNTAX_TOKEN_UNEXPECTED | 1;
	
	/**
	 * A token (represented by an node) is missing.
	 */
	public static final int STATUS1_SYNTAX_MISSING_TOKEN=                       0x1300;
	public static final int STATUS2_SYNTAX_EXPR_AS_REF_MISSING=                 0x1310 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS2_SYNTAX_EXPR_BEFORE_OP_MISSING=              0x1320 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS2_SYNTAX_EXPR_AFTER_OP_MISSING=               0x1330 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS2_SYNTAX_EXPR_AS_CONDITION_MISSING=           0x1340 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS2_SYNTAX_EXPR_AS_FORSEQ_MISSING=              0x1350 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS2_SYNTAX_EXPR_AS_BODY_MISSING=                0x1360 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS2_SYNTAX_EXPR_IN_GROUP_MISSING=               0x1370 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS2_SYNTAX_EXPR_AS_ARGVALUE_MISSING=            0x1380 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS2_SYNTAX_ELEMENTNAME_MISSING=                 0x1390 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS2_SYNTAX_SYMBOL_MISSING=                      0x13f0 | STATUSFLAG_REAL_ERROR;
//	public static final SyntaxValidity P_MISSING_EXPR_STATUS= new SyntaxValidity(SyntaxValidity.ERROR,
//	P_CAT_SYNTAX_FLAG | 0x210,
//	"Syntax Error/Missing Expression: a valid expression is expected.");
	
	public static final int STATUS2_SYNTAX_OPERATOR_MISSING=                    0x1410 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS2_SYNTAX_FCALL_NOT_CLOSED=                    0x1420 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS2_SYNTAX_SUBINDEXED_NOT_CLOSED=               0x1430 | STATUSFLAG_REAL_ERROR;
	
	/**
	 * A control statement (part of an existing node) is incomplete.
	 */
	public static final int STATUS1_SYNTAX_INCOMPLETE_CC=                       0x1500;
	public static final int STATUS2_SYNTAX_CC_NOT_CLOSED=                       0x1510 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS2_SYNTAX_IF_MISSING=                          0x1530 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS2_SYNTAX_CONDITION_MISSING=                   0x1540 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS2_SYNTAX_IN_MISSING=                          0x1550 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS2_SYNTAX_CONDITION_NOT_CLOSED=                0x1560 | STATUSFLAG_REAL_ERROR;
	
	/**
	 * A function definition is incomplete.
	 */
	public static final int STATUS1_SYNTAX_INCOMPLETE_FDEF=                     0x1600;
	public static final int STATUS2_SYNTAX_FDEF_ARGS_MISSING=                   0x1610 | STATUSFLAG_REAL_ERROR;
	public static final int STATUS2_SYNTAX_FDEF_ARGS_NOT_CLOSED=                0x1620 | STATUSFLAG_REAL_ERROR;
	
	public static final int STATUS3_IF=                     1;
	public static final int STATUS3_ELSE=                   2;
	public static final int STATUS3_FOR=                    3;
	public static final int STATUS3_WHILE=                  4;
	public static final int STATUS3_REPEAT=                 5;
	public static final int STATUS3_FDEF=                   6;
	public static final int STATUS3_FCALL=                  7;
	
}
