import type { BlocRendu } from '../types'
import {
  BlocInconnu,
  Countdown,
  CtaAdhesion,
  Embed,
  EventList,
  Faq,
  Gallery,
  Hero,
  Partners,
  RichText,
  Stats,
  TeamGrid,
} from './Blocs'

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
  /** Le mandat de la page est-il celui en fonction ? L'agenda s'intitule
   *  autrement sur une archive, où plus rien n'est « à venir ». */
  estCourant: boolean
}

export function RendreBloc({ bloc, slug, anneeCode, estCourant }: ContexteBloc) {
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
    case 'EVENT_LIST':
      return <EventList bloc={bloc} anneeCode={anneeCode} estCourant={estCourant} />
    case 'PARTNERS':
      return <Partners bloc={bloc} />
    case 'EMBED':
      return <Embed bloc={bloc} />
    case 'COUNTDOWN':
      return <Countdown bloc={bloc} />
    default:
      // Un type au registre côté serveur mais absent de ce bundle : ignoré
      // proprement, jamais planté.
      return <BlocInconnu bloc={bloc} />
  }
}
