package com.cobbletracker.common.util;

import java.util.regex.Pattern;

public final class ChatLocationTemplate {
    private static final Pattern FORMAT = Pattern.compile("<[^>]*>|[&§][0-9a-fk-or]", Pattern.CASE_INSENSITIVE);
    private static final Pattern COORDINATES = Pattern.compile(
            "(?:[\\(\\[]\\s*)?%(?:waypoint|x|y|z)%(?:(?:\\s*[,;/|:]?\\s*)%[xyz]%)*(?:\\s*[\\)\\]])?");
    private static final Pattern CONNECTOR = Pattern.compile("\\s+(?:at|near|на|біля|возле|около)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SPACING = Pattern.compile("\\s+(?=[,.!?;])|[ \\t]+(?=[ \\t])");
    private static final Pattern EMPTY_TAG = Pattern.compile("<([^<>/\\s]+)>\\s*</\\1>");

    private ChatLocationTemplate() { }

    public static String prepare(String template, boolean showLocation) {
        if (template == null) return null;
        if (showLocation) return template.replace("%location%", " <gray>at</gray> %waypoint%");
        String result = removeVisible(template.replace("%location%", ""), COORDINATES, true);
        for (String token : new String[] {"%waypoint%", "%x%", "%y%", "%z%"}) {
            result = result.replace(token, "");
        }
        result = removeVisible(result, SPACING, false);
        String previous;
        do {
            previous = result;
            result = EMPTY_TAG.matcher(result).replaceAll("");
        } while (!previous.equals(result));
        return result.trim();
    }

    private static String removeVisible(String template, Pattern pattern, boolean removeConnector) {
        StringBuilder visible = new StringBuilder();
        int[] positions = new int[template.length()];
        var formatting = FORMAT.matcher(template);
        int next = 0;
        while (formatting.find()) {
            for (int i = next; i < formatting.start(); i++) {
                positions[visible.length()] = i;
                visible.append(template.charAt(i));
            }
            next = formatting.end();
        }
        for (int i = next; i < template.length(); i++) {
            positions[visible.length()] = i;
            visible.append(template.charAt(i));
        }
        boolean[] removed = new boolean[template.length()];
        var matches = pattern.matcher(visible);
        while (matches.find()) {
            int start = matches.start();
            if (removeConnector) {
                var connector = CONNECTOR.matcher(visible.substring(0, start));
                if (connector.find()) start = connector.start();
            }
            for (int i = start; i < matches.end(); i++) removed[positions[i]] = true;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < template.length(); i++) {
            if (!removed[i]) result.append(template.charAt(i));
        }
        return result.toString();
    }
}
