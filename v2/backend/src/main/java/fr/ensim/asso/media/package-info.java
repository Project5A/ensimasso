/**
 * Médiathèque — dépôt, métadonnées et résolution d'URL des objets binaires.
 *
 * <p>Règle fondatrice du module : la base ne stocke <strong>que la clé</strong>
 * d'objet. L'URL est fabriquée à la lecture. La v1 persistait une URL signée
 * valable 24 h, ce qui fait mourir chaque image un jour après son dépôt.
 *
 * <p><strong>Ce qui est vérifié au dépôt.</strong> Le droit de déposer sur
 * l'association ; la taille réelle de l'objet ; et la signature de ses premiers
 * octets, comparée au type annoncé. Ce dernier point n'est pas redondant avec
 * la liste blanche de types : le dépôt est direct vers le stockage, avec un PUT
 * signé dont le navigateur choisit l'en-tête {@code Content-Type}. Relire ce
 * type auprès de S3 revient à relire le client.
 *
 * <p><strong>Ce qui ne l'est pas, et qui reste à faire.</strong> Le retrait des
 * métadonnées EXIF — un JPEG de téléphone porte les coordonnées GPS de l'endroit
 * où il a été pris, et une photo de soirée publiée sur un site public les
 * publie avec — la génération des variantes WebP/AVIF (la table
 * {@code media_variante} existe et n'est pas alimentée), et l'analyse
 * antivirale. Ces trois opérations supposent de <em>décoder</em> le fichier, ce
 * qui ne doit pas se produire dans ce processus : c'est le travail du worker
 * média, isolé, sans accès à la base. Comparer des octets à des constantes,
 * comme le fait {@code SignatureFichier}, n'invoque aucun analyseur — c'est ce
 * qui rend cette vérification-ci sûre en ligne.
 *
 * <p><strong>Risque résiduel assumé.</strong> Les objets sont servis par le
 * stockage, pas par cette application : nos en-têtes de sécurité ne s'y
 * appliquent pas. La vérification de signature garantit que ce qui est servi
 * est bien du type annoncé, ce qui referme l'essentiel du problème ; le reste
 * se traite en servant la médiathèque depuis une origine distincte.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Média",
        allowedDependencies = {
            "gouvernance :: domain", "gouvernance :: app",
            "shared"})
package fr.ensim.asso.media;
