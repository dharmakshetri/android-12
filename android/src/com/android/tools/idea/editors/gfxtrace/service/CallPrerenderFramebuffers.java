/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * THIS FILE WAS GENERATED BY codergen. EDIT WITH CARE.
 */
package com.android.tools.idea.editors.gfxtrace.service;

import com.android.tools.idea.editors.gfxtrace.service.path.CapturePath;
import com.android.tools.idea.editors.gfxtrace.service.path.DevicePath;
import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

final class CallPrerenderFramebuffers implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  DevicePath myDevice;
  CapturePath myCapture;
  BinaryID myApi;
  int myWidth;
  int myHeight;
  long[] myAtomIndicies;

  // Constructs a default-initialized {@link CallPrerenderFramebuffers}.
  public CallPrerenderFramebuffers() {}


  public DevicePath getDevice() {
    return myDevice;
  }

  public CallPrerenderFramebuffers setDevice(DevicePath v) {
    myDevice = v;
    return this;
  }

  public CapturePath getCapture() {
    return myCapture;
  }

  public CallPrerenderFramebuffers setCapture(CapturePath v) {
    myCapture = v;
    return this;
  }

  public BinaryID getApi() {
    return myApi;
  }

  public CallPrerenderFramebuffers setApi(BinaryID v) {
    myApi = v;
    return this;
  }

  public int getWidth() {
    return myWidth;
  }

  public CallPrerenderFramebuffers setWidth(int v) {
    myWidth = v;
    return this;
  }

  public int getHeight() {
    return myHeight;
  }

  public CallPrerenderFramebuffers setHeight(int v) {
    myHeight = v;
    return this;
  }

  public long[] getAtomIndicies() {
    return myAtomIndicies;
  }

  public CallPrerenderFramebuffers setAtomIndicies(long[] v) {
    myAtomIndicies = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }

  private static final byte[] IDBytes = {-77, -71, 31, -22, -68, 126, -8, 56, 45, -8, 93, -106, 33, 5, -8, 78, 19, -95, -31, 62, };
  public static final BinaryID ID = new BinaryID(IDBytes);

  static {
    Namespace.register(ID, Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public BinaryID id() { return ID; }

    @Override @NotNull
    public BinaryObject create() { return new CallPrerenderFramebuffers(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      CallPrerenderFramebuffers o = (CallPrerenderFramebuffers)obj;
      e.object(o.myDevice);
      e.object(o.myCapture);
      e.id(o.myApi);
      e.uint32(o.myWidth);
      e.uint32(o.myHeight);
      e.uint32(o.myAtomIndicies.length);
      for (int i = 0; i < o.myAtomIndicies.length; i++) {
        e.uint64(o.myAtomIndicies[i]);
      }
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      CallPrerenderFramebuffers o = (CallPrerenderFramebuffers)obj;
      o.myDevice = (DevicePath)d.object();
      o.myCapture = (CapturePath)d.object();
      o.myApi = d.id();
      o.myWidth = d.uint32();
      o.myHeight = d.uint32();
      o.myAtomIndicies = new long[d.uint32()];
      for (int i = 0; i <o.myAtomIndicies.length; i++) {
        o.myAtomIndicies[i] = d.uint64();
      }
    }
    //<<<End:Java.KlassBody:2>>>
  }
}