package com.example.test

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Conservative adapter for the Douyin recommendation feed; unknown screens pass through. */
object DouyinFeedRegion {
    fun find(root: AccessibilityNodeInfo): Rect? {
        if (root.packageName != TargetAccessibilityService.TARGETS[0]) return null
        var video: Rect? = null
        var recommendation = false
        var recommendationBottom = 0
        var home = false
        var blocked = false
        var visited = 0
        fun visit(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 60 || ++visited > 1000) { blocked = true; return }
            val bounds = Rect().also(node::getBoundsInScreen)
            val description = node.contentDescription?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()
            val type = node.className?.toString().orEmpty()
            if (description == "视频" && type.contains("ViewPager") && bounds.height() > bounds.width()) {
                video = bounds
                // Video contents are irrelevant to the adapter; avoid traversing captions and adjacent items.
                return
            }
            val descriptionParts = description.split('，', ',').map { it.trim() }
            if (node.isVisibleToUser && "推荐" in descriptionParts && "已选中" in descriptionParts) {
                recommendation = true
                recommendationBottom = bounds.bottom
            }
            if (node.isVisibleToUser && text == "首页") home = true
            if (node.isVisibleToUser && (node.isEditable || type.contains("EditText") ||
                ((type.contains("RecyclerView") || type.contains("ListView")) && bounds.height() > 100) ||
                text.matches(Regex(".*[0-9万]+.*条评论.*")) || text == "全部评论")) blocked = true
            for (i in 0 until node.childCount) node.getChild(i)?.let { child ->
                visit(child, depth + 1)
                @Suppress("DEPRECATION") child.recycle()
            }
        }
        visit(root, 0)
        if (blocked || !recommendation || !home) return null
        return video?.let { bounds ->
            // Cover the feed width so edge swipes also pass through the gate. Tabs stay native;
            // taps on video controls are replayed by ActionIntervention.
            Rect(bounds.left, maxOf(bounds.top, recommendationBottom), bounds.right, bounds.bottom)
                .takeIf { it.width() > 0 && it.height() > 0 }
        }
    }
}
