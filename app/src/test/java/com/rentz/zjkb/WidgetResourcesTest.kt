package com.rentz.zjkb

import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class WidgetResourcesTest {
    private val android = "http://schemas.android.com/apk/res/android"
    private fun xml(path: String) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(File("src/main/res/$path"))

    @Test fun everyAndroidVersionUsesTheNativeInitialLayout() {
        listOf("xml", "xml-v31").forEach { directory ->
            val root = xml("$directory/today_widget_info.xml").documentElement
            val layout = root.getAttributeNS(android, "initialLayout")
            assertEquals("@layout/widget_agenda", layout)
            assertTrue(File("src/main/res/layout/${layout.substringAfter('/')}.xml").exists())
            assertEquals("horizontal|vertical", root.getAttributeNS(android, "resizeMode"))
        }
    }

    @Test fun remoteLayoutsOnlyContainLauncherSupportedViews() {
        val supported = setOf("LinearLayout", "FrameLayout", "TextView", "ImageView", "ListView")
        listOf("widget_agenda", "widget_agenda_course", "widget_agenda_preview", "widget_agenda_compact").forEach { name ->
            val elements = xml("layout/$name.xml").getElementsByTagName("*")
            for (index in 0 until elements.length) {
                assertTrue("Unsupported remote view in $name: ${elements.item(index).nodeName}",
                    elements.item(index).nodeName in supported)
            }
        }
    }

    @Test fun courseFieldsCanGrowInsideAScrollableAgenda() {
        assertEquals(1, xml("layout/widget_agenda.xml").getElementsByTagName("ListView").length)
        val elements = xml("layout/widget_agenda_course.xml").getElementsByTagName("TextView")
        val required = mutableSetOf("course_name", "course_room", "course_teachers")
        for (index in 0 until elements.length) {
            val view = elements.item(index) as Element
            if (required.remove(view.getAttributeNS(android, "id").substringAfter('/'))) {
                assertEquals("wrap_content", view.getAttributeNS(android, "layout_height"))
                assertFalse(view.hasAttributeNS(android, "maxLines"))
                assertFalse(view.hasAttributeNS(android, "ellipsize"))
            }
        }
        assertTrue("Course fields missing from remote layout", required.isEmpty())
    }
}
