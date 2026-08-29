import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import { fallbackLanguage, resources, supportedLanguages, type SupportedLanguage } from './locales'

const LANGUAGE_STORAGE_KEY = 'windrunner.language'
const supportedLanguageCodes = new Set<string>(supportedLanguages.map(({ code }) => code))

function languageCode(value: string | null | undefined): SupportedLanguage | null {
  if (!value) {
    return null
  }

  const normalized = value.toLowerCase().split('-')[0]
  return supportedLanguageCodes.has(normalized) ? normalized as SupportedLanguage : null
}

function initialLanguage(): SupportedLanguage {
  if (typeof window !== 'undefined') {
    try {
      const storedLanguage = languageCode(window.localStorage.getItem(LANGUAGE_STORAGE_KEY))
      if (storedLanguage) {
        return storedLanguage
      }
    } catch {
      // Continue with the browser language when storage is unavailable.
    }

    for (const candidate of window.navigator.languages ?? []) {
      const browserLanguage = languageCode(candidate)
      if (browserLanguage) {
        return browserLanguage
      }
    }

    const browserLanguage = languageCode(window.navigator.language)
    if (browserLanguage) {
      return browserLanguage
    }
  }

  return fallbackLanguage
}

const selectedLanguage = initialLanguage()

void i18n
  .use(initReactI18next)
  .init({
    resources,
    lng: selectedLanguage,
    fallbackLng: fallbackLanguage,
    defaultNS: 'translation',
    interpolation: {
      escapeValue: false,
    },
    react: {
      useSuspense: false,
    },
  })

if (typeof document !== 'undefined') {
  document.documentElement.lang = selectedLanguage
}

i18n.on('languageChanged', (language) => {
  const nextLanguage = languageCode(language) ?? fallbackLanguage
  if (typeof document !== 'undefined') {
    document.documentElement.lang = nextLanguage
  }
  if (typeof window !== 'undefined') {
    try {
      window.localStorage.setItem(LANGUAGE_STORAGE_KEY, nextLanguage)
    } catch {
      // Keep the language for the current session when storage is unavailable.
    }
  }
})

export { supportedLanguages }
export type { SupportedLanguage }
export default i18n
