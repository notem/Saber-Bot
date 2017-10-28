package ws.nmathe.saber.core.google;

import org.jsoup.Jsoup;
import org.jsoup.helper.StringUtil;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

/**
 *  Recent versions of Google Calendar embed HTML tags into the
 *  descriptions of events. These tags must be removed for parsing
 *  by the CalendarConverter class.
 */
public class HTMLStripper
{
    /**
     * removes HTML tags from a google calendar event's description
     * @param description  an event description possibly containing HTML tags
     * @return an event description free of HTML tags
     */
    public static String cleanDescription(String description)
    {
        FormattingVisitor formatter = new FormattingVisitor();
        new NodeTraversor(formatter).traverse(Jsoup.parse(description));
        return formatter.toString();
    }

    /**
     * HTML Node traversal scheme to reduce an html page into plaintext
     *
     * Refer to:
     * https://github.com/jhy/jsoup/blob/master/src/main/java/org/jsoup/examples/HtmlToPlainText.java
     */
    private static class FormattingVisitor implements NodeVisitor
    {
        private StringBuilder accum = new StringBuilder(); // holds the accumulated text

        // hit when the node is first seen
        public void head(Node node, int i)
        {
            String name = node.nodeName();
            if (node instanceof TextNode)
                accum.append(((TextNode) node).text()); // TextNodes carry all user-readable text in the DOM.
            else if (name.equals("li"))
                accum.append("\n * ");
            else if (name.equals("dt"))
                accum.append("  ");
            else if (StringUtil.in(name, "p", "h1", "h2", "h3", "h4", "h5", "tr"))
                accum.append("\n");
        }

        // hit when all of the node's children (if any) have been visited
        public void tail(Node node, int depth)
        {
            String name = node.nodeName();
            if (StringUtil.in(name, "br", "dd", "dt", "p", "h1", "h2", "h3", "h4", "h5"))
                accum.append("\n");
        }

        @Override
        public String toString()
        {
            return accum.toString();
        }
    }
}
