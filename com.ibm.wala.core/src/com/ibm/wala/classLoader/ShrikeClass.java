/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.classLoader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrikeBT.Constants;
import com.ibm.wala.shrikeCT.AnnotationsReader;
import com.ibm.wala.shrikeCT.ClassConstants;
import com.ibm.wala.shrikeCT.ClassReader;
import com.ibm.wala.shrikeCT.ClassReader.AttrIterator;
import com.ibm.wala.shrikeCT.InnerClassesReader;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.shrikeCT.RuntimeInvisibleAnnotationsReader;
import com.ibm.wala.shrikeCT.RuntimeVisibleAnnotationsReader;
import com.ibm.wala.shrikeCT.SignatureReader;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.types.generics.ClassSignature;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.shrike.ShrikeClassReaderHandle;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.strings.ImmutableByteArray;

/**
 * A class read from Shrike
 */
public final class ShrikeClass extends JVMClass<IClassLoader> {

  static final boolean DEBUG = false;

  /**
   * The Shrike object that knows how to read the class file
   */
  private final ShrikeClassReaderHandle reader;
  
  /**
   * @throws IllegalArgumentException if reader is null
   */
  public ShrikeClass(ShrikeClassReaderHandle reader, IClassLoader loader, IClassHierarchy cha) throws InvalidClassFileException {
    super(loader, cha);
    if (reader == null) {
      throw new IllegalArgumentException("reader is null");
    }
    this.reader = reader;
    computeTypeReference();
    this.hashCode = 2161 * getReference().hashCode();
    // as long as the reader is around, pull more data out
    // of it before the soft reference to it disappears
    computeSuperName();
    computeModifiers();
    computeInterfaceNames();
    computeFields();
  }

  /**
   * Compute the fields declared by this class
   * 
   * @throws InvalidClassFileException iff Shrike fails to read the class file correctly
   */
  private void computeFields() throws InvalidClassFileException {
    ClassReader cr = reader.get();
    int fieldCount = cr.getFieldCount();
    List<FieldImpl> instanceList = new ArrayList<FieldImpl>(fieldCount);
    List<FieldImpl> staticList = new ArrayList<FieldImpl>(fieldCount);
    try {
      for (int i = 0; i < fieldCount; i++) {
        int accessFlags = cr.getFieldAccessFlags(i);
        Atom name = Atom.findOrCreateUnicodeAtom(cr.getFieldName(i));
        ImmutableByteArray b = ImmutableByteArray.make(cr.getFieldType(i));
        Collection<Annotation> annotations = null;
          annotations = getRuntimeInvisibleAnnotations(i);
          annotations = annotations.isEmpty() ? null : annotations;

        if ((accessFlags & ClassConstants.ACC_STATIC) == 0) {
          addFieldToList(instanceList, name, b, accessFlags, annotations);
        } else {
          addFieldToList(staticList, name, b, accessFlags, annotations);
        }
      }
      instanceFields = new IField[instanceList.size()];
      populateFieldArrayFromList(instanceList, instanceFields);
      staticFields = new IField[staticList.size()];
      populateFieldArrayFromList(staticList, staticFields);

    } catch (InvalidClassFileException e) {
      e.printStackTrace();
      Assertions.UNREACHABLE();
    }
  }

  /**
   * @throws InvalidClassFileException
   */
  private void computeModifiers() throws InvalidClassFileException {
    modifiers = reader.get().getAccessFlags();
  }

   /**
   * Note that this is called from the constructor, at which point this class is not yet ready to actually load the superclass.
   * Instead, we pull out the name of the superclass and cache it here, to avoid hitting the reader later.
   */
  private void computeSuperName() {
    try {
      String s = reader.get().getSuperName();
      if (s != null) {
        superName = ImmutableByteArray.make("L" + s);
      }
    } catch (InvalidClassFileException e) {
      Assertions.UNREACHABLE();
    }
  }

  /**
   * Note that this is called from the constructor, at which point this class is not yet ready to actually load the interfaces.
   * Instead, we pull out the name of the interfaces and cache it here, to avoid hitting the reader later.
   */
  private void computeInterfaceNames() {
    try {
      String[] s = reader.get().getInterfaceNames();
      interfaceNames = new ImmutableByteArray[s.length];
      for (int i = 0; i < interfaceNames.length; i++) {
        interfaceNames[i] = ImmutableByteArray.make("L" + s[i]);
      }
    } catch (InvalidClassFileException e) {
      Assertions.UNREACHABLE();
    }
  }

  /**
   * initialize the declared methods array
   * 
   * @throws InvalidClassFileException
   */
  @Override
  protected ShrikeCTMethod[] computeDeclaredMethods() throws InvalidClassFileException {
    int methodCount = reader.get().getMethodCount();
    ShrikeCTMethod[] result = new ShrikeCTMethod[methodCount];
    for (int i = 0; i < methodCount; i++) {
      ShrikeCTMethod m = new ShrikeCTMethod(this, i);
      if (DEBUG) {
        System.err.println(("Register method " + m + " for class " + this));
      }
      result[i] = m;
    }
    return result;
  }

 
  /**
   * initialize the TypeReference field for this instance
   * 
   * @throws InvalidClassFileException iff Shrike can't read this class
   */
  private void computeTypeReference() throws InvalidClassFileException {
    String className = "L" + reader.get().getName();
    ImmutableByteArray name = ImmutableByteArray.make(className);

    typeReference = TypeReference.findOrCreate(getClassLoader().getReference(), TypeName.findOrCreate(name));
  }

  
  /**
   * @see java.lang.Object#equals(Object)
   */
  @Override
  public boolean equals(Object obj) {
    // it's ok to use instanceof since this class is final
    // if (this.getClass().equals(obj.getClass())) {
    if (obj instanceof ShrikeClass) {
      return getReference().equals(((ShrikeClass) obj).getReference());
    } else {
      return false;
    }
  }

  public ClassReader getReader() {
    try {
      return reader.get();
    } catch (InvalidClassFileException e) {
      e.printStackTrace();
      Assertions.UNREACHABLE();
      return null;
    }
  }
  
  /**
   * Clear all optional cached data associated with this class
   */
  public void clearSoftCaches() {
    // toss optional information from each method.
    if (methodMap != null) {
      for (Iterator it = getDeclaredMethods().iterator(); it.hasNext();) {
        ShrikeCTMethod m = (ShrikeCTMethod) it.next();
        m.clearCaches();
      }
    }
    // clear the methodMap cache
    // SJF: don't do this!!! makes it hard to clear caches on methods.
    // methodMap = null;
    inheritCache = null;
    // clear the cached interfaces
    allInterfaces = null;
    // toss away the Shrike reader
    reader.clear();
  }

  public Collection<Annotation> getRuntimeInvisibleAnnotations() throws InvalidClassFileException {
    return getAnnotations(true);
  }

  public Collection<Annotation> getRuntimeVisibleAnnotations() throws InvalidClassFileException {
    return getAnnotations(false);
  }
  
  public Collection<Annotation> getAnnotations(boolean runtimeInvisible) throws InvalidClassFileException {
    AnnotationsReader r = getAnnotationsReader(runtimeInvisible);
    return Annotation.getAnnotationsFromReader(r, getClassLoader().getReference());
  }

  private AnnotationsReader getAnnotationsReader(boolean runtimeInvisable) throws InvalidClassFileException {
    ClassReader r = reader.get();
    ClassReader.AttrIterator attrs = new ClassReader.AttrIterator();
    r.initClassAttributeIterator(attrs);

    // search for the desired attribute
    AnnotationsReader result = null;
    try {
      for (; attrs.isValid(); attrs.advance()) {
        if (runtimeInvisable){
          if (attrs.getName().equals(RuntimeInvisibleAnnotationsReader.attrName)) {
            result = new RuntimeInvisibleAnnotationsReader(attrs);
            break;
          }
        } else {
          if (attrs.getName().equals(RuntimeVisibleAnnotationsReader.attrName)) {
            result = new RuntimeVisibleAnnotationsReader(attrs);
            break;
          }
        }
      }
    } catch (InvalidClassFileException e) {
      Assertions.UNREACHABLE();
    }
    return result;
  }

  
  private InnerClassesReader getInnerClassesReader() throws InvalidClassFileException {
    ClassReader r = reader.get();
    ClassReader.AttrIterator attrs = new ClassReader.AttrIterator();
    r.initClassAttributeIterator(attrs);

    // search for the desired attribute
    InnerClassesReader result = null;
    try {
      for (; attrs.isValid(); attrs.advance()) {
        if (attrs.getName().equals("InnerClasses")) {
          result = new InnerClassesReader(attrs);
          break;
        }
      }
    } catch (InvalidClassFileException e) {
      Assertions.UNREACHABLE();
    }
    return result;
  }

  private RuntimeInvisibleAnnotationsReader getRuntimeInvisibleAnnotationsReader(int fieldIndex) throws InvalidClassFileException {
    ClassReader.AttrIterator iter = new AttrIterator();
    reader.get().initFieldAttributeIterator(fieldIndex, iter);

    // search for the desired attribute
    RuntimeInvisibleAnnotationsReader result = null;
    try {
      for (; iter.isValid(); iter.advance()) {
        if (iter.getName().toString().equals("RuntimeInvisibleAnnotations")) {
          result = new RuntimeInvisibleAnnotationsReader(iter);
          break;
        }
      }
    } catch (InvalidClassFileException e) {
      Assertions.UNREACHABLE();
    }
    return result;
  }

  /**
   * read the runtime-invisible annotations from the class file
   */
  public Collection<Annotation> getRuntimeInvisibleAnnotations(int fieldIndex) throws InvalidClassFileException {
    RuntimeInvisibleAnnotationsReader r = getRuntimeInvisibleAnnotationsReader(fieldIndex);
    return Annotation.getAnnotationsFromReader(r, getClassLoader().getReference());
  }

  private SignatureReader getSignatureReader() throws InvalidClassFileException {
    ClassReader r = reader.get();
    ClassReader.AttrIterator attrs = new ClassReader.AttrIterator();
    r.initClassAttributeIterator(attrs);

    // search for the desired attribute
    SignatureReader result = null;
    try {
      for (; attrs.isValid(); attrs.advance()) {
        if (attrs.getName().toString().equals("Signature")) {
          result = new SignatureReader(attrs);
          break;
        }
      }
    } catch (InvalidClassFileException e) {
      Assertions.UNREACHABLE();
    }
    return result;
  }

  public ClassSignature getClassSignature() throws InvalidClassFileException {
    // TODO: cache this later?
    SignatureReader r = getSignatureReader();
    if (r == null) {
      return null;
    } else {
      return ClassSignature.make(r.getSignature());
    }
  }

  public ModuleEntry getModuleEntry() {
    return reader.getModuleEntry();
  }

  /**
   * Does the class file indicate that this class is a member of some other class?
   * 
   * @throws InvalidClassFileException
   */
  public boolean isInnerClass() throws InvalidClassFileException {
    InnerClassesReader r = getInnerClassesReader();
    if (r != null) {
      for (String s : r.getInnerClasses()) {
        if (s.equals(getName().toString().substring(1))) {
          String outer = r.getOuterClass(s);
          return outer != null;
        }
      }
    }
    return false;
  }

  /**
   * Does the class file indicate that this class is a static inner class?
   * 
   * @throws InvalidClassFileException
   */
  public boolean isStaticInnerClass() throws InvalidClassFileException {
    InnerClassesReader r = getInnerClassesReader();
    if (r != null) {
      for (String s : r.getInnerClasses()) {
        if (s.equals(getName().toString().substring(1))) {
          String outer = r.getOuterClass(s);
          if (outer != null) {
            int modifiers = r.getAccessFlags(s);
            boolean result = ((modifiers & Constants.ACC_STATIC) != 0);
            return result;
          }
        }
      }
    }
    return false;
  }
  
  /**
   * If this is an inner class, return the outer class.   Else return null.
   * @throws InvalidClassFileException 
   */
  public TypeReference getOuterClass() throws InvalidClassFileException {
    if (!isInnerClass()) {
      return null;
    }
    InnerClassesReader r = getInnerClassesReader();
    for (String s : r.getInnerClasses()) {
      if (s.equals(getName().toString().substring(1))) {
        String outer = r.getOuterClass(s);
        if (outer != null) {
          return TypeReference.findOrCreate(getClassLoader().getReference(), "L" + outer);
        }
      }
    }
    return null;
  }
}