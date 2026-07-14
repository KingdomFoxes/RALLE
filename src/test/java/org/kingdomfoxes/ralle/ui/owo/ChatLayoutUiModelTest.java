package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.core.VerticalAlignment;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatLayoutUiModelTest {
    @Test
    void usesVerticalAlignmentsSupportedByOwo() throws Exception {
        try (var model = getClass().getResourceAsStream("/assets/ralle/owo_ui/chat_layout.xml")) {
            assertNotNull(model, "chat layout UI model must be packaged");
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(model);
            var alignments = document.getElementsByTagName("vertical-alignment");

            for (var index = 0; index < alignments.getLength(); index++) {
                var value = alignments.item(index).getTextContent().strip().toUpperCase(Locale.ROOT);
                assertDoesNotThrow(() -> VerticalAlignment.valueOf(value));
            }
        }
    }

    @Test
    void exposesEveryRequestedEditorControl() throws Exception {
        try (var model = getClass().getResourceAsStream("/assets/ralle/owo_ui/chat_layout.xml")) {
            assertNotNull(model, "chat layout UI model must be packaged");
            var document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(model);
            var expectedIds = java.util.Set.of(
                    "free-move", "snap-grid", "alignment-guides",
                    "show-all", "show-grid", "position-info", "reset-button", "done-button"
            );
            var foundIds = new java.util.HashSet<String>();
            var elements = document.getElementsByTagName("*");
            for (var index = 0; index < elements.getLength(); index++) {
                var id = elements.item(index).getAttributes().getNamedItem("id");
                if (id != null && expectedIds.contains(id.getNodeValue())) foundIds.add(id.getNodeValue());
            }

            assertEquals(expectedIds, foundIds);
        }
    }
}
