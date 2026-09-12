import type { BlocRendu } from '../types'
import { BlocInconnu, CtaAdhesion, Faq, Gallery, Hero, RichText, Stats, TeamGrid } from './Blocs'

/**
 * Le registre : un type de bloc, un composant.
 *
 * <p>Ajouter un type de bloc = une ligne ici et un composant. Aucune migration,
 * aucun changement du constructeur de pages, puisque le formulaire d'édition est
 * généré depuis le JSON Schema servi par l'API. C'est la forme la plus forte que
 * puisse prendre la promesse « les assos personnalisent leurs pages sans
 * intervention d'un développeur » — et la revue a été claire sur le fait qu'on
 * doit l'énoncer exactement comme ça, sans laisser croire qu'un NOUVEAU type de
 * bloc n'exige aucun développement.
 */
export type ContexteBloc = {
  bloc: BlocRendu
  slug: string
  anneeCode: string
}

export function RendreBloc({ bloc, slug, anneeCode }: ContexteBloc) {
  switch (bloc.type) {
    case 'HERO':
      return <Hero bloc={bloc} />
    case 'RICH_TEXT':
      return <RichText bloc={bloc} />
    case 'STATS':
      return <Stats bloc={bloc} />
    case 'TEAM_GRID':
      return <TeamGrid bloc={bloc} anneeCode={anneeCode} />
    case 'FAQ':
      return <Faq bloc={bloc} />
    case 'CTA_ADHESION':
      return <CtaAdhesion bloc={bloc} slug={slug} />
    case 'GALLERY':
      return <Gallery bloc={bloc} />
    default:
      // EVENT_LIST, PARTNERS, COUNTDOWN, EMBED : au registre côté serveur,
      // pas encore rendus. Ils sont ignorés, pas plantés.
      return <BlocInconnu bloc={bloc} />
  }
}
