import { BookOpenText, CheckCircle2, Code2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

type Step = {
  number: string
  titleKey: string
  bodyKey: string
  codeKey: string
}

const steps: Step[] = [
  { number: '01', titleKey: 'docs.localization.stepOneTitle', bodyKey: 'docs.localization.stepOneBody', codeKey: 'docs.localization.stepOneCode' },
  { number: '02', titleKey: 'docs.localization.stepTwoTitle', bodyKey: 'docs.localization.stepTwoBody', codeKey: 'docs.localization.stepTwoCode' },
  { number: '03', titleKey: 'docs.localization.stepThreeTitle', bodyKey: 'docs.localization.stepThreeBody', codeKey: 'docs.localization.stepThreeCode' },
  { number: '04', titleKey: 'docs.localization.stepFourTitle', bodyKey: 'docs.localization.stepFourBody', codeKey: 'docs.localization.stepFourCode' },
  { number: '05', titleKey: 'docs.localization.stepFiveTitle', bodyKey: 'docs.localization.stepFiveBody', codeKey: 'docs.localization.stepFiveCode' },
]

export default function DocumentationPage() {
  const { t } = useTranslation()

  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col overflow-auto bg-background">
      <main className="mx-auto w-full max-w-5xl space-y-8 px-6 py-8 md:px-10 md:py-10">
        <header className="max-w-3xl space-y-3">
          <div className="flex items-center gap-2 text-sm font-medium text-primary">
            <BookOpenText className="h-4 w-4" aria-hidden="true" />
            <span>{t('docs.title')}</span>
          </div>
          <h1 className="text-3xl font-semibold tracking-tight">{t('docs.localization.title')}</h1>
          <p className="text-sm font-medium text-muted-foreground">{t('docs.subtitle')}</p>
          <p className="text-base leading-7 text-muted-foreground">{t('docs.localization.intro')}</p>
        </header>

        <Card className="border-primary/20 bg-primary/[0.03]">
          <CardHeader>
            <p className="text-xs font-semibold uppercase tracking-wide text-primary">{t('docs.localization.eyebrow')}</p>
            <CardTitle className="text-xl">{t('docs.localization.workflowTitle')}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            {steps.map((step) => (
              <article key={step.number} className="grid gap-4 border-t pt-5 first:border-t-0 first:pt-0 md:grid-cols-[3rem_minmax(0,1fr)]">
                <div className="flex items-center gap-2 text-sm font-semibold text-primary md:block">
                  <span>{step.number}</span>
                  <CheckCircle2 className="h-4 w-4 md:mt-2" aria-hidden="true" />
                </div>
                <div className="min-w-0 space-y-2">
                  <h2 className="text-base font-semibold">{t(step.titleKey)}</h2>
                  <p className="text-sm leading-6 text-muted-foreground">{t(step.bodyKey)}</p>
                  <pre className="overflow-x-auto rounded-md border bg-background p-3 font-mono text-xs leading-5 text-foreground"><code>{t(step.codeKey)}</code></pre>
                </div>
              </article>
            ))}
          </CardContent>
        </Card>

        <div className="grid gap-6 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-base"><Code2 className="h-4 w-4" aria-hidden="true" />{t('docs.localization.conventionsTitle')}</CardTitle>
            </CardHeader>
            <CardContent>
              <ul className="space-y-3 text-sm leading-6 text-muted-foreground">
                <li>{t('docs.localization.conventionOne')}</li>
                <li>{t('docs.localization.conventionTwo')}</li>
                <li>{t('docs.localization.conventionThree')}</li>
                <li>{t('docs.localization.conventionFour')}</li>
              </ul>
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle className="text-base">{t('docs.localization.sourceTitle')}</CardTitle>
            </CardHeader>
            <CardContent>
              <p className="text-sm leading-6 text-muted-foreground">{t('docs.localization.sourceBody')}</p>
            </CardContent>
          </Card>
        </div>
      </main>
    </div>
  )
}
