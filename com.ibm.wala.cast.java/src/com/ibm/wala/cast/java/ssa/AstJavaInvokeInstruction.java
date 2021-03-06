/******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *****************************************************************************/
package com.ibm.wala.cast.java.ssa;

import java.util.Collection;

import com.ibm.wala.cast.ir.ssa.AstLexicalAccess.Access;
import com.ibm.wala.cast.ir.ssa.FixedParametersLexicalInvokeInstruction;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.JavaLanguage;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.TypeReference;

/**
 * This is a normal Java invoke instruction as generated by the CAst source language front end; its only difference from the normal
 * SSAInvokeInstruction is that it is subclassed from invoke instructions that support explicit handling of lexical scoping. So it
 * behaves exactly like an invoke from bytecode, except that it has extra state for managing lexical uses and definitions.
 * 
 * @author Julian Dolby (dolby@us.ibm.com)
 */
public class AstJavaInvokeInstruction extends FixedParametersLexicalInvokeInstruction {

  protected AstJavaInvokeInstruction(int results[], int[] params, int exception, CallSiteReference site) {
    super(results, params, exception, site);
  }

  public AstJavaInvokeInstruction(int result, int[] params, int exception, CallSiteReference site) {
    this(new int[] { result }, params, exception, site);
    SSAInvokeInstruction.assertParamsKosher(result, params, site);
  }

  /**
   * Constructor InvokeInstruction. This case for void return values
   */
  public AstJavaInvokeInstruction(int[] params, int exception, CallSiteReference site) {
    this(null, params, exception, site);
  }

  public AstJavaInvokeInstruction(int results[], int[] params, int exception, CallSiteReference site, Access[] lexicalReads,
      Access[] lexicalWrites) {
    super(results, params, exception, site, lexicalReads, lexicalWrites);
  }

  protected SSAInstruction copyInstruction(SSAInstructionFactory insts, int results[], int[] params, int exception,
      Access[] lexicalReads, Access[] lexicalWrites) {
    return ((AstJavaInstructionFactory) insts).JavaInvokeInstruction(results, params, exception, getCallSite(), lexicalReads,
        lexicalWrites);
  }

  /**
   * @see com.ibm.domo.ssa.SSAInstruction#visit(IVisitor)
   */
  public void visit(IVisitor v) {
    ((AstJavaInstructionVisitor) v).visitJavaInvoke(this);
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.ibm.domo.ssa.Instruction#getExceptionTypes()
   */
  public Collection<TypeReference> getExceptionTypes() {
    return JavaLanguage.getNullPointerException();
  }

}
