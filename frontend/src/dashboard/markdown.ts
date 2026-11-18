import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'

// The Markdown of a text widget (US-059, design D4 of us-055-dashboards). Raw HTML in the source is
// shown as text (`html: false`); the output passes through DOMPurify with an allowlist of tags and
// attributes and of the URL schemes http, https and mailto, so that no script, event handler or
// unknown tag reaches the page. The result is the only content the page sets with v-html.
const markdown = new MarkdownIt({ html: false, linkify: false, breaks: false })

const ALLOWED_TAGS = [
  'p', 'br', 'strong', 'em', 'del', 'code', 'pre', 'blockquote', 'ul', 'ol', 'li', 'a', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'hr',
  'table', 'thead', 'tbody', 'tr', 'th', 'td',
]
const ALLOWED_ATTR = ['href', 'title', 'rel', 'target']
const ALLOWED_URI_REGEXP = /^(?:https?:|mailto:)/i

const purifier = DOMPurify(window)

// links open outside of the application and keep no reference to it
purifier.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A') {
    node.setAttribute('rel', 'noopener noreferrer')
    node.setAttribute('target', '_blank')
  }
})

/** The sanitized HTML of Markdown text. */
export function renderMarkdown(source: string): string {
  return purifier.sanitize(markdown.render(source), { ALLOWED_TAGS, ALLOWED_ATTR, ALLOWED_URI_REGEXP })
}
