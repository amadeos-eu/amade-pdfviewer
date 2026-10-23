package eu.amadeos.apps.pdfviewer.ui.main;

import eu.amadeos.apps.pdfviewer.bootstrap.PdfViewerModule;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Mnemonics;
import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationTest {
    private static Messages texts(Language language) {
        return Messages.load(PdfViewerModule.class, PdfViewerModule.MESSAGES, Locale.forLanguageTag(language.code()));
    }

    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(PdfViewerModule.class, PdfViewerModule.MESSAGES));
    }

    /** No two entries share a mnemonic letter in the menu bar or in a menu. */
    @Test
    void mnemonicsAreUnique() {
        Map<String, List<String>> groups = new HashMap<>();
        groups.put("bar", List.of("menu.file", "menu.edit", "menu.view", "menu.go", "menu.options", "menu.help"));
        groups.put("file", List.of("menu.file.open", "menu.file.recent", "menu.file.reload", "menu.file.print",
                "menu.file.properties", "menu.file.exit"));
        groups.put("edit", List.of("menu.edit.copy", "menu.edit.select-all", "menu.edit.find", "menu.edit.find-next",
                "menu.edit.find-previous"));
        groups.put("view", List.of("menu.view.sidebar", "menu.view.pages", "menu.view.contents",
                "menu.view.continuous", "menu.view.single", "menu.view.two-pages", "menu.view.zoom-in",
                "menu.view.zoom-out", "menu.view.actual-size", "menu.view.fit-page", "menu.view.fit-width",
                "menu.view.rotate-left", "menu.view.rotate-right", "menu.view.presentation"));
        groups.put("go", List.of("menu.go.previous", "menu.go.next", "menu.go.first", "menu.go.last", "menu.go.page",
                "menu.go.back", "menu.go.forward"));
        for (Language language : Language.values()) {
            Messages texts = texts(language);
            for (Map.Entry<String, List<String>> group : groups.entrySet()) {
                Map<Integer, String> used = new HashMap<>();
                for (String key : group.getValue()) {
                    Mnemonics.Text text = Mnemonics.parse(texts.get(key));
                    assertTrue(text.index() >= 0, language + " " + key + " has no mnemonic");
                    String before = used.put(text.keyCode(), key);
                    assertEquals(null, before, language + " " + group.getKey() + ": " + key + " and " + before
                            + " share a letter");
                }
            }
        }
    }

    @Test
    void windowTitles() {
        assertEquals("PDF-Betrachter", texts(Language.GERMAN).get("window.title"));
        assertEquals("Visionneuse PDF", texts(Language.FRENCH).get("window.title"));
    }
}
