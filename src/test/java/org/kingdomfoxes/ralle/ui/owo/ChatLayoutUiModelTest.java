package org.kingdomfoxes.ralle.ui.owo;

import io.wispforest.owo.ui.core.VerticalAlignment;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
