import en from './en'
import zh from './zh'

export const resources = {
  en: { translation: en },
  zh: { translation: zh },
} as const

export const supportedLanguages = [
  { code: 'en', label: 'English' },
  { code: 'zh', label: '简体中文' },
] as const

export type SupportedLanguage = (typeof supportedLanguages)[number]['code']

export const fallbackLanguage: SupportedLanguage = 'en'
