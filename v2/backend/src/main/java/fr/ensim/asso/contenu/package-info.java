/**
 * Contenu — pages, versions, blocs, thèmes, registre des types de blocs.
 *
 * <p>Dépend de gouvernance pour vérifier les droits sur un mandat, et de
 * média pour une seule question : à la publication, les clés écrites dans les
 * payloads désignent-elles des médias que cette page a le droit de servir ?
 * La clé étrangère de {@code media_usage} ne répondait qu'au commit, en
 * PostgreSQL, et seulement à la moitié de la question.
 *
 * <p><strong>Ce qui reste bancal, et qui n'est pas corrigé ici.</strong>
 * {@code media_usage} est à la frontière des deux modules : elle référence
 * {@code page_version} (contenu) et {@code media_asset} (média). Elle vit du
 * côté contenu, qui l'écrit ; mais {@code MediaAssetRepository.utilisationsFigees}
 * la relit depuis le module média, par une requête SQL native — donc par un
 * couplage en sens inverse que Modulith ne peut pas voir et ne signalera
 * jamais. C'est le point à reprendre si cette frontière doit être nettoyée.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Contenu",
        allowedDependencies = {
            "gouvernance :: domain", "gouvernance :: app",
            "media :: domain",
            "shared"})
package fr.ensim.asso.contenu;
