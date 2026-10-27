package io.github.mutebutton.musicwidget.uiprobe;

import android.app.Instrumentation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.Xml;
import org.xmlpull.v1.XmlSerializer;
import java.io.File;
import java.io.FileOutputStream;

/** Test-only APK. Reads live accessibility without waiting for animated UI to become idle. */
public class UiProbe extends Instrumentation {
    private String focusResource;
    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        focusResource = args == null ? null : args.getString("focusResource");
        start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            AccessibilityServiceInfo info = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).setServiceInfo(info);
            SystemClock.sleep(250);
            AccessibilityNodeInfo root = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();
            if (root == null) throw new IllegalStateException("No active window");
            if (focusResource != null) {
                AccessibilityNodeInfo target = findResource(root, focusResource);
                if (target == null || !target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS))
                    throw new IllegalStateException("Cannot focus requested resource");
                SystemClock.sleep(300);
                root = getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).getRootInActiveWindow();
            }
            try (FileOutputStream output = new FileOutputStream(new File(getContext().getFilesDir(), "window.xml"))) {
                XmlSerializer xml = Xml.newSerializer();
                xml.setOutput(output, "UTF-8");
                xml.startDocument("UTF-8", true);
                xml.startTag(null, "hierarchy");
                writeNode(xml, root);
                xml.endTag(null, "hierarchy");
                xml.endDocument();
            }
            result.putString("result", "UI captured");
            finish(0, result);
        } catch (Exception error) {
            result.putString("error", error.toString());
            finish(1, result);
        }
    }
    private AccessibilityNodeInfo findResource(AccessibilityNodeInfo node, String id) {
        if (id.equals(node.getViewIdResourceName())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo found = findResource(child, id);
            if (found != null) return found;
        }
        return null;
    }
    private void writeNode(XmlSerializer xml, AccessibilityNodeInfo node) throws Exception {
        if (node == null || !node.isVisibleToUser()) return;
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        xml.startTag(null, "node");
        xml.attribute(null, "text", value(node.getText()));
        xml.attribute(null, "content-desc", value(node.getContentDescription()));
        xml.attribute(null, "resource-id", value(node.getViewIdResourceName()));
        xml.attribute(null, "class", value(node.getClassName()));
        xml.attribute(null, "package", value(node.getPackageName()));
        xml.attribute(null, "enabled", String.valueOf(node.isEnabled()));
        xml.attribute(null, "clickable", String.valueOf(node.isClickable()));
        xml.attribute(null, "important", String.valueOf(node.isImportantForAccessibility()));
        xml.attribute(null, "accessibility-focused", String.valueOf(node.isAccessibilityFocused()));
        xml.attribute(null, "focusable", String.valueOf(node.isFocusable()));
        xml.attribute(null, "bounds", "["+bounds.left+","+bounds.top+"]["+bounds.right+","+bounds.bottom+"]");
        for (int i=0; i<node.getChildCount(); i++) writeNode(xml, node.getChild(i));
        xml.endTag(null, "node");
    }
    private String value(CharSequence text) { return text == null ? "" : text.toString(); }
}
