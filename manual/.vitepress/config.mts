import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { defineConfig, type DefaultTheme } from 'vitepress'

// ADR-045 rules 1 and 2: English root locale, German under /de/, the same chapters and pages in
// both languages (checked by scripts/check_manual.py). Change s2c-manual-site, design D1, D2, D5.

type Lang = 'en' | 'de'

interface Page {
  path: string
  title: Record<Lang, string>
}

interface Chapter {
  dir: string
  title: Record<Lang, string>
  pages: Page[]
}

// Shared chapter list; every chapter is a directory with an index.md in both locales
const chapters: Chapter[] = [
  {
    dir: 'introduction',
    title: { en: 'Introduction', de: 'Einführung' },
    pages: [{ path: '', title: { en: 'What OEA is', de: 'Was OEA ist' } }],
  },
  {
    dir: 'setup',
    title: { en: 'Setup and first steps', de: 'Einrichtung und erste Schritte' },
    pages: [
      { path: '', title: { en: 'Overview', de: 'Überblick' } },
      { path: 'installation', title: { en: 'Installation', de: 'Installation' } },
      { path: 'first-administrator', title: { en: 'First administrator', de: 'Erster Administrator' } },
    ],
  },
  {
    dir: 'administration',
    title: { en: 'Administration', de: 'Administration' },
    pages: [
      { path: '', title: { en: 'Overview', de: 'Überblick' } },
      { path: 'identity-providers', title: { en: 'Identity providers and persons', de: 'Identity-Provider und Personen' } },
      { path: 'roles-and-permissions', title: { en: 'Roles and permissions', de: 'Rollen und Berechtigungen' } },
      { path: 'metamodel-packages', title: { en: 'Metamodel packages', de: 'Metamodell-Pakete' } },
      { path: 'metamodel-editing', title: { en: 'Editing the metamodel', de: 'Metamodell bearbeiten' } },
      { path: 'operations', title: { en: 'Operations', de: 'Betrieb' } },
    ],
  },
  {
    dir: 'usage',
    title: { en: 'Usage', de: 'Nutzung' },
    pages: [
      { path: '', title: { en: 'Overview', de: 'Überblick' } },
      { path: 'login', title: { en: 'Logging in', de: 'Anmelden' } },
      { path: 'catalogs', title: { en: 'Catalogs', de: 'Kataloge' } },
      { path: 'entities-batch', title: { en: 'Writing entities in batch', de: 'Entitäten im Batch schreiben' } },
    ],
  },
  {
    dir: 'api',
    title: { en: 'API reference', de: 'API-Referenz' },
    pages: [{ path: '', title: { en: 'Using the API', de: 'Die API verwenden' } }],
  },
]

// Written by scripts/generate-api-reference.mjs (design D4); absent before the first generation
interface ReferencePage {
  slug: string
  title: string
}
const referenceFile = fileURLToPath(new URL('./api-reference.json', import.meta.url))
const referencePages: ReferencePage[] = existsSync(referenceFile)
  ? JSON.parse(readFileSync(referenceFile, 'utf8'))
  : []

// Written by scripts/screenshots.mjs (change s2c-manual-release, design D3): languages and names of
// the screenshots produced by the walkthrough tests; empty without a walkthrough run
interface Screenshots {
  languages: string[]
  names: Record<string, string[]>
}
const screenshotsFile = fileURLToPath(new URL('./screenshots.json', import.meta.url))
const screenshots: Screenshots = existsSync(screenshotsFile)
  ? JSON.parse(readFileSync(screenshotsFile, 'utf8'))
  : { languages: [], names: {} }

function prefix(lang: Lang): string {
  return lang === 'en' ? '/' : '/de/'
}

function sidebar(lang: Lang): DefaultTheme.SidebarItem[] {
  return chapters.map((chapter) => {
    const items: DefaultTheme.SidebarItem[] = chapter.pages.map((page) => ({
      text: page.title[lang],
      link: `${prefix(lang)}${chapter.dir}/${page.path}`,
    }))
    if (chapter.dir === 'api') {
      for (const page of referencePages) {
        items.push({ text: page.title, link: `${prefix(lang)}api/reference/${page.slug}` })
      }
    }
    return { text: chapter.title[lang], collapsed: false, items }
  })
}

function nav(lang: Lang): DefaultTheme.NavItem[] {
  return chapters.map((chapter) => ({
    text: chapter.title[lang],
    link: `${prefix(lang)}${chapter.dir}/`,
    activeMatch: `^${prefix(lang)}${chapter.dir}/`,
  }))
}

// Design D4, D5 (change s2c-manual-release): MANUAL_BASE is the base of this build (site root, or a
// version directory like /OEA/v0.1/ or /OEA/next/); MANUAL_SITE_ROOT is the root of the Pages site,
// where versions.json lives
const base = process.env.MANUAL_BASE || '/'
const siteRoot = process.env.MANUAL_SITE_ROOT || base
if (!base.startsWith(siteRoot)) throw new Error(`MANUAL_BASE ${base} is not below MANUAL_SITE_ROOT ${siteRoot}`)

export default defineConfig({
  title: 'OEA',
  base,
  srcExclude: ['README.md', 'scripts/**', 'walkthrough/**', 'screenshots/**', 'test-results/**', 'playwright-report/**'],
  cleanUrls: true,
  themeConfig: {
    // Design D5: set on release builds (change s2c-manual-release); empty for the build of main
    manualVersion: process.env.MANUAL_VERSION || '',
    manualSiteRoot: siteRoot,
    // Directory of this build below the site root: '' (root), 'v0.1/' or 'next/'
    manualDirectory: base.slice(siteRoot.length),
    screenshots,
    socialLinks: [{ icon: 'github', link: 'https://github.com/open-enterprise-architecture/OEA' }],
    search: {
      provider: 'local',
      options: {
        locales: {
          de: {
            translations: {
              button: { buttonText: 'Suchen', buttonAriaLabel: 'Suchen' },
              modal: {
                displayDetails: 'Details anzeigen',
                resetButtonTitle: 'Suche zurücksetzen',
                backButtonTitle: 'Suche schließen',
                noResultsText: 'Keine Ergebnisse für',
                footer: {
                  selectText: 'auswählen',
                  navigateText: 'navigieren',
                  closeText: 'schließen',
                },
              },
            },
          },
        },
      },
    },
  },
  locales: {
    root: {
      label: 'English',
      lang: 'en',
      description: 'User manual of OEA – Open Enterprise Architecture',
      themeConfig: { nav: nav('en'), sidebar: sidebar('en') },
    },
    de: {
      label: 'Deutsch',
      lang: 'de',
      link: '/de/',
      description: 'Benutzerhandbuch von OEA – Open Enterprise Architecture',
      themeConfig: {
        nav: nav('de'),
        sidebar: sidebar('de'),
        outline: { label: 'Auf dieser Seite' },
        docFooter: { prev: 'Vorherige Seite', next: 'Nächste Seite' },
        darkModeSwitchLabel: 'Darstellung',
        lightModeSwitchTitle: 'Zum hellen Modus wechseln',
        darkModeSwitchTitle: 'Zum dunklen Modus wechseln',
        sidebarMenuLabel: 'Menü',
        returnToTopLabel: 'Zurück nach oben',
        langMenuLabel: 'Sprache ändern',
        skipToContentLabel: 'Zum Inhalt springen',
        notFound: {
          title: 'Seite nicht gefunden',
          quote: 'Diese Seite gibt es in diesem Handbuch nicht.',
          linkText: 'Zur Startseite',
        },
      },
    },
  },
})
