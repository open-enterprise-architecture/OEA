import { describe, expect, it } from 'vitest'
import { renderMarkdown } from './markdown'

// spec dashboard/widgets "Text widget" (US-059 AC2, AC3; design D4 of us-055-dashboards)
describe('renderMarkdown', () => {
  it('renders bold text, a list and an https link as HTML', () => {
    const html = renderMarkdown('**bold**\n\n- one\n- two\n\n[guide](https://example.org/guide)')
    const element = document.createElement('div')
    element.innerHTML = html
    expect(element.querySelector('strong')?.textContent).toBe('bold')
    expect([...element.querySelectorAll('li')].map((item) => item.textContent)).toEqual(['one', 'two'])
    const link = element.querySelector('a')
    expect(link?.getAttribute('href')).toBe('https://example.org/guide')
    expect(link?.getAttribute('rel')).toBe('noopener noreferrer')
  })

  it.each(['https://example.org/', 'http://example.org/', 'mailto:kurt@example.org'])('keeps the link %s', (href) => {
    expect(renderMarkdown(`[x](${href})`)).toContain(`href="${href}"`)
  })

  it.each(['javascript:alert(1)', 'data:text/html;base64,PHNjcmlwdD4=', 'vbscript:x', 'file:///etc/passwd', 'ftp://example.org/'])(
    'makes the link %s inert',
    (href) => {
      const element = document.createElement('div')
      element.innerHTML = renderMarkdown(`[x](${href})`)
      // no link with a target remains: the element is dropped or its href removed
      expect(element.querySelector('a[href]')).toBeNull()
    },
  )

  it('shows raw HTML as text instead of running it', () => {
    const element = document.createElement('div')
    element.innerHTML = renderMarkdown('<script>alert(1)</script> <img src=x onerror=alert(1)>')
    expect(element.querySelector('script')).toBeNull()
    expect(element.querySelector('img')).toBeNull()
    expect(element.textContent).toContain('<script>alert(1)</script>')
    expect(element.innerHTML).not.toContain('<img')
  })

  it('creates no element with an event handler and no unknown tag', () => {
    const element = document.createElement('div')
    element.innerHTML = renderMarkdown('<div onclick="x()">a</div>\n\n<iframe src="https://example.org"></iframe>\n\n**ok**')
    expect(element.querySelector('div, iframe')).toBeNull()
    expect([...element.querySelectorAll('*')].some((node) => node.hasAttribute('onclick'))).toBe(false)
    expect(element.querySelector('strong')?.textContent).toBe('ok')
  })

  it('renders an empty content as nothing', () => {
    expect(renderMarkdown('')).toBe('')
  })
})
