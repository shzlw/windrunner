import en from './en'

export const resources = {
  en: { translation: en },
} as const

export const supportedLanguages = [
  { code: 'en', label: 'English' },
] as const

export type SupportedLanguage = (typeof supportedLanguages)[number]['code']

export const fallbackLanguage: SupportedLanguage = 'en'
