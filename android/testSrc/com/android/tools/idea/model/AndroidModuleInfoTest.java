/*
 * Copyright (C) 2013 The Android Open Source Project
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
 */
package com.android.tools.idea.model;

import com.android.tools.idea.gradle.eclipse.GradleImport;
import com.android.tools.idea.run.activity.ActivityLocatorUtils;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.UIUtil;
import junit.framework.AssertionFailedError;
import org.jetbrains.android.facet.AndroidRootUtil;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.android.tools.idea.testing.TestProjectPaths.*;
import static com.google.common.truth.Truth.assertThat;

public class AndroidModuleInfoTest extends AndroidGradleTestCase {
  public void testManifestOnly() throws Exception {
    loadProject(MODULE_INFO_MANIFEST_ONLY);
    assertNotNull(myAndroidFacet);
    assertEquals(7, myAndroidFacet.getAndroidModuleInfo().getMinSdkVersion().getApiLevel());
    assertEquals(18, myAndroidFacet.getAndroidModuleInfo().getTargetSdkVersion().getApiLevel());
    //noinspection SpellCheckingInspection
    assertEquals("com.example.unittest", myAndroidFacet.getAndroidModuleInfo().getPackage());
  }

  public void testGradleOnly() throws Exception {
    loadProject(MODULE_INFO_GRADLE_ONLY);
    assertNotNull(myAndroidFacet);
    assertEquals(9, myAndroidFacet.getAndroidModuleInfo().getMinSdkVersion().getApiLevel());
    assertEquals(GradleImport.CURRENT_COMPILE_VERSION, myAndroidFacet.getAndroidModuleInfo().getTargetSdkVersion().getApiLevel());
    assertEquals("from.gradle", myAndroidFacet.getAndroidModuleInfo().getPackage());
  }

  public void testBoth() throws Exception {
    loadProject(MODULE_INFO_BOTH);
    assertNotNull(myAndroidFacet);
    assertEquals(9, myAndroidFacet.getAndroidModuleInfo().getMinSdkVersion().getApiLevel());
    assertEquals(GradleImport.CURRENT_COMPILE_VERSION, myAndroidFacet.getAndroidModuleInfo().getTargetSdkVersion().getApiLevel());
    assertEquals("from.gradle", myAndroidFacet.getAndroidModuleInfo().getPackage());
  }

  public void testFlavors() throws Exception {
    loadProject(MODULE_INFO_FLAVORS);
    assertNotNull(myAndroidFacet);

    assertEquals(14, myAndroidFacet.getAndroidModuleInfo().getMinSdkVersion().getApiLevel());
    assertEquals(GradleImport.CURRENT_COMPILE_VERSION, myAndroidFacet.getAndroidModuleInfo().getTargetSdkVersion().getApiLevel());
    assertEquals("com.example.free.debug", myAndroidFacet.getAndroidModuleInfo().getPackage());
  }

  public void testMerge() throws Exception {
    loadProject(MODULE_INFO_MERGE);
    assertNotNull(myAndroidFacet);

    MergedManifest manifestInfo = MergedManifest.get(myAndroidFacet);
    List<Element> mergedActivities = manifestInfo.getActivities();
    assertEquals(2, mergedActivities.size());
    Set<String> activities = Sets.newHashSet(ActivityLocatorUtils.getQualifiedName(mergedActivities.get(0)),
                                             ActivityLocatorUtils.getQualifiedName(mergedActivities.get(1)));
    assertTrue(activities.contains("com.example.unittest.Main"));
    assertTrue(activities.contains("from.gradle.debug.Debug"));

    List<Element> services = manifestInfo.getServices();
    assertEquals(1, services.size());
    assertEquals("com.example.unittest.WatchFace", ActivityLocatorUtils.getQualifiedName(services.get(0)));

    assertEquals("@style/AppTheme", manifestInfo.getManifestTheme());
  }

  public void testManifestPlaceholderCompletion() throws Exception {
    loadProject(MODULE_INFO_MERGE);
    assertNotNull(myAndroidFacet);
    VirtualFile file = getProject().getBaseDir().findFileByRelativePath("src/main/AndroidManifest.xml");
    assertNotNull(file);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(file);
    assertNotNull(psiFile);

    myFixture.configureFromExistingVirtualFile(file);
    List<HighlightInfo> highlights = myFixture.doHighlighting(HighlightSeverity.ERROR);
    assertTrue(highlights.isEmpty());
    myFixture.testHighlighting(false, false, false, file);

    final PsiDocumentManager manager = PsiDocumentManager.getInstance(getProject());
    final Document document = manager.getDocument(psiFile);
    assertNotNull(document);

    final String defaultPlaceholder = "${defaultTheme}";

    // Check placeholder completion
    new WriteCommandAction.Simple(getProject(), psiFile) {
      @Override
      protected void run() throws Throwable {
        int offset = document.getText().indexOf(defaultPlaceholder);
        document.replaceString(offset, offset + defaultPlaceholder.length(), "${<caret>");
        manager.commitAllDocuments();
      }
    }.execute();
    FileDocumentManager.getInstance().saveAllDocuments();
    myFixture.configureFromExistingVirtualFile(file);

    myFixture.complete(CompletionType.BASIC);
    assertThat(myFixture.getLookupElementStrings()).containsExactly("defaultTheme");
    // Check that there are no errors
    myFixture.testHighlighting();
  }

  public void testManifestMerger() throws Exception {
    loadProject(MODULE_INFO_MANIFEST_MERGER);
    assertNotNull(myAndroidFacet);
    assertEquals(1, AndroidUtils.getAllAndroidDependencies(myAndroidFacet.getModule(), true).size());

    MergedManifest manifestInfo = MergedManifest.get(myAndroidFacet);
    List<Element> mergedActivities = manifestInfo.getActivities();
    assertEquals(3, mergedActivities.size());
    Set<String> activities = Sets.newHashSet(ActivityLocatorUtils.getQualifiedName(mergedActivities.get(0)),
                                             ActivityLocatorUtils.getQualifiedName(mergedActivities.get(1)),
                                             ActivityLocatorUtils.getQualifiedName(mergedActivities.get(2)));
    assertTrue(activities.contains("test.helloworldapp.Debug"));
    assertTrue(activities.contains("test.helloworldapp.MyActivity"));
    assertTrue(activities.contains("test.helloworldapp.lib.LibActivity"));

    assertNotNull(manifestInfo.getVersionCode());
    assertEquals(2, manifestInfo.getVersionCode().intValue());

    // Make a change to the psi
    VirtualFile manifestFile = AndroidRootUtil.getPrimaryManifestFile(myAndroidFacet);
    assertNotNull(manifestFile);
    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(manifestFile);
    assertNotNull(psiFile);
    new WriteCommandAction.Simple(getProject(), psiFile) {
      @Override
      protected void run() throws Throwable {
        assertNotNull(myAndroidFacet.getManifest());
        XmlTag manifestTag = myAndroidFacet.getManifest().getXmlTag();
        XmlTag applicationTag = Iterables.find(Arrays.asList(manifestTag.getSubTags()), new Predicate<XmlTag>() {
          @Override
          public boolean apply(@Nullable XmlTag input) {
            assertNotNull(input);
            return "application".equals(input.getName());
          }
        });
        XmlTag activityTag = applicationTag.createChildTag("activity", "", null, false);
        activityTag.setAttribute("android:name", ".AddedActivity");
        applicationTag.addSubTag(activityTag, false);
      }
    }.execute();
    UIUtil.dispatchAllInvocationEvents();

    // reload data and check it is correct
    manifestInfo.clear();
    mergedActivities = manifestInfo.getActivities();
    assertEquals(4, mergedActivities.size());
    activities = Sets.newHashSet(ActivityLocatorUtils.getQualifiedName(mergedActivities.get(0)),
                                 ActivityLocatorUtils.getQualifiedName(mergedActivities.get(1)),
                                 ActivityLocatorUtils.getQualifiedName(mergedActivities.get(2)),
                                 ActivityLocatorUtils.getQualifiedName(mergedActivities.get(3)));
    assertTrue(activities.contains("test.helloworldapp.Debug"));
    assertTrue(activities.contains("test.helloworldapp.MyActivity"));
    assertTrue(activities.contains("test.helloworldapp.lib.LibActivity"));
    assertTrue(activities.contains("test.helloworldapp.AddedActivity"));

    // make a change to the psi
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(getProject());
    final Document document = documentManager.getDocument(psiFile);
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        assertNotNull(document);
        int index = document.getText().indexOf("</application>");
        document.insertString(index, "<activity android:name=\".AddedActivity2\" />\n");
        documentManager.commitDocument(document);
      }
    });
    UIUtil.dispatchAllInvocationEvents();

    // reload data and check it is correct
    manifestInfo.clear();
    mergedActivities = manifestInfo.getActivities();
    assertEquals(5, mergedActivities.size());
    activities = Sets.newHashSet(ActivityLocatorUtils.getQualifiedName(mergedActivities.get(0)),
                                 ActivityLocatorUtils.getQualifiedName(mergedActivities.get(1)),
                                 ActivityLocatorUtils.getQualifiedName(mergedActivities.get(2)),
                                 ActivityLocatorUtils.getQualifiedName(mergedActivities.get(3)),
                                 ActivityLocatorUtils.getQualifiedName(mergedActivities.get(4)));
    assertTrue(activities.contains("test.helloworldapp.Debug"));
    assertTrue(activities.contains("test.helloworldapp.MyActivity"));
    assertTrue(activities.contains("test.helloworldapp.lib.LibActivity"));
    assertTrue(activities.contains("test.helloworldapp.AddedActivity"));
    assertTrue(activities.contains("test.helloworldapp.AddedActivity2"));
  }

  public void testManifestError() throws Exception {
    try {
      loadProject(MODULE_INFO_MANIFEST_ERROR);
      fail();
    } catch (AssertionFailedError e) {
      assertThat(e.getMessage()).contains("Exception while parsing the supplied manifest file");
    }
  }
}
