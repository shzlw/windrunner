import { useTranslation } from 'react-i18next'

import { NativeSelect, NativeSelectOption } from '@/components/ui/native-select'
import { supportedLanguages, type SupportedLanguage } from '@/i18n'

export default function LanguageSelect() {
  const { t, i18n } = useTranslation()
  const currentLanguage = (i18n.resolvedLanguage ?? i18n.language).split('-')[0] as SupportedLanguage

  return (
    <div className="space-y-2">
      <label htmlFor="language-select" className="block text-sm font-semibold">
        {t('language.label')}
      </label>
      <NativeSelect
        id="language-select"
        value={supportedLanguages.some(({ code }) => code === currentLanguage) ? currentLanguage : 'en'}
        onChange={(event) => void i18n.changeLanguage(event.target.value)}
        aria-label={t('language.label')}
        className="w-full max-w-xs"
      >
        {supportedLanguages.map((language) => (
          <NativeSelectOption key={language.code} value={language.code}>
            {language.label}
          </NativeSelectOption>
        ))}
      </NativeSelect>
    </div>
  )
}
