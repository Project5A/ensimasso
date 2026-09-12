package fr.ensim.asso.contenu.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.*;
import fr.ensim.asso.contenu.domain.TypeBloc;
import fr.ensim.asso.contenu.domain.TypeBlocRepository;
import fr.ensim.asso.shared.error.Erreurs;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Valide le payload d'un bloc contre le JSON Schema de sa version de type.
 *
 * <p>Deux règles issues de la revue d'architecture :
 * <ul>
 *   <li>la validation vise toujours la version de schéma <em>enregistrée sur
 *       le bloc</em>, jamais « la version courante ». Sinon une version
 *       rédigée sous le schéma v1 et republiée après un durcissement en v2
 *       serait rejetée — et la republication sert justement à 23 h la veille
 *       du gala, le pire moment pour découvrir un 422 ;</li>
 *   <li>les schémas sont compilés une fois et mis en cache par
 *       {@code (type, version)}, parce qu'ils sont immuables par construction.</li>
 * </ul>
 */
@Component
public class ValidationBloc {

    private final TypeBlocRepository typesBlocs;
    private final ObjectMapper mapper;
    private final JsonSchemaFactory factory =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    private final Map<String, JsonSchema> cache = new ConcurrentHashMap<>();

    public ValidationBloc(TypeBlocRepository typesBlocs, ObjectMapper mapper) {
        this.typesBlocs = typesBlocs;
        this.mapper = mapper;
    }

    /**
     * @throws PayloadInvalideException si le payload ne respecte pas le schéma
     * @throws TypeBlocInconnuException si (type, version) n'existe pas au registre
     */
    public void valider(String type, int schemaVersion, String payloadJson) {
        JsonSchema schema = cache.computeIfAbsent(type + "@" + schemaVersion, k -> {
            TypeBloc tb = typesBlocs.findById(new TypeBloc.Cle(type, schemaVersion))
                    .orElseThrow(() -> new Erreurs.RequeteInvalide("type de bloc inconnu au registre : " + type + "@" + schemaVersion));
            try {
                return factory.getSchema(mapper.readTree(tb.getJsonSchema()));
            } catch (Exception e) {
                throw new IllegalStateException(
                        "schéma illisible pour " + type + "@" + schemaVersion, e);
            }
        });

        JsonNode payload;
        try {
            payload = mapper.readTree(payloadJson);
        } catch (Exception e) {
            throw new Erreurs.ContenuInvalide("payload illisible pour le bloc " + type, List.of("JSON illisible"));
        }

        Set<ValidationMessage> erreurs = schema.validate(payload);
        if (!erreurs.isEmpty()) {
            throw new Erreurs.ContenuInvalide(
                    "le bloc " + type + "@" + schemaVersion + " ne respecte pas son schema",
                    erreurs.stream().map(ValidationMessage::getMessage).sorted().toList());
        }
    }

    /** La version de schéma à utiliser pour un bloc NOUVELLEMENT créé. */
    public int versionCourantePour(String type) {
        return typesBlocs.versionCourante(type)
                .orElseThrow(() -> new Erreurs.RequeteInvalide("type de bloc inconnu au registre : " + type))
                .getSchemaVersion();
    }

}
