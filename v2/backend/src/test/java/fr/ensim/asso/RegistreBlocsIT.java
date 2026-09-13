package fr.ensim.asso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le registre des types de blocs, tel qu'il existe VRAIMENT après migration.
 *
 * <p>Vérifier les fichiers de migration un par un ne suffirait pas : une
 * migration ultérieure peut resserrer un schéma posé par une précédente, et
 * c'est exactement ce que fait V9. Ce qui compte est l'état final, celui que
 * la validation appliquera.
 */
class RegistreBlocsIT extends BaseIT {

    /** Noms de champ qui annoncent une URL destinée à un attribut href. */
    private static final Set<String> CHAMPS_URL = Set.of("href", "url", "lien", "site");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("tout champ d'URL du registre contraint les schémas qu'il accepte")
    void urlsContraintes() throws Exception {
        List<Map<String, Object>> types = jdbc.queryForList(
                "SELECT type, schema_version, json_schema::text AS schema FROM type_bloc");

        assertThat(types)
                .as("le registre doit être peuplé par les migrations")
                .isNotEmpty();

        List<String> nus = new ArrayList<>();
        for (Map<String, Object> t : types) {
            JsonNode schema = MAPPER.readTree((String) t.get("schema"));
            chercher(schema, "", t.get("type") + "@" + t.get("schema_version"), nus);
        }

        // Le registre avait délibérément supprimé l'injection de HTML — RICH_TEXT
        // stocke un document structuré, EMBED n'accepte qu'une liste blanche de
        // fournisseurs. Le raisonnement n'avait jamais été porté jusqu'aux champs
        // d'URL : `href` était une chaîne de 512 caractères, sans motif.
        // `javascript:alert(document.cookie)` traversait la validation et
        // arrivait intact dans le href d'une page publique.
        assertThat(nus)
                .as("un champ d'URL sans motif ni énumération accepte javascript: "
                  + "et data:, que le navigateur exécute depuis un href")
                .isEmpty();
    }

    @Test
    @DisplayName("le payload de départ de chaque type est valide pour SON schéma")
    void payloadsDeDepartValides() throws Exception {
        List<Map<String, Object>> types = jdbc.queryForList(
                "SELECT type, schema_version, json_schema::text AS schema, "
              + "payload_defaut::text AS defaut FROM type_bloc");

        assertThat(types).isNotEmpty();

        var factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        List<String> invalides = new ArrayList<>();
        for (Map<String, Object> t : types) {
            var schema = factory.getSchema(MAPPER.readTree((String) t.get("schema")));
            var erreurs = schema.validate(MAPPER.readTree((String) t.get("defaut")));
            if (!erreurs.isEmpty()) {
                invalides.add(t.get("type") + "@" + t.get("schema_version") + " : "
                        + erreurs.iterator().next().getMessage());
            }
        }

        // La palette créait tout bloc avec un payload vide. Huit schémas sur
        // onze déclarent des propriétés requises : huit types de blocs — dont
        // la bannière, le texte et le trombinoscope — étaient refusés en 422 et
        // ne pouvaient pas être créés. Personne ne s'en apercevait parce que
        // rien ne confrontait jamais le payload de création au schéma.
        assertThat(invalides)
                .as("un type dont le payload de départ ne passe pas son propre "
                  + "schéma est un type que personne ne peut ajouter à une page")
                .isEmpty();
    }

    /** Descend le schéma et relève tout champ d'URL qui n'impose aucune forme. */
    private static void chercher(JsonNode noeud, String chemin, String type, List<String> nus) {
        if (!noeud.isObject()) {
            return;
        }
        JsonNode proprietes = noeud.get("properties");
        if (proprietes != null && proprietes.isObject()) {
            proprietes.properties().forEach(e -> {
                JsonNode def = e.getValue();
                String sousChemin = chemin + "." + e.getKey();
                if (CHAMPS_URL.contains(e.getKey())
                        && def.path("type").asText("").equals("string")
                        && !def.has("pattern") && !def.has("enum") && !def.has("format")) {
                    nus.add(type + " : " + sousChemin.substring(1));
                }
                chercher(def, sousChemin, type, nus);
            });
        }
        JsonNode items = noeud.get("items");
        if (items != null) {
            chercher(items, chemin + "[]", type, nus);
        }
    }
}
