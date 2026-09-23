package schultz.thomas.schub.connector.riot.business.services;

import org.bson.Document;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static schultz.thomas.schub.connector.riot.business.services.MatchEnrichmentService.enObjet;
import static schultz.thomas.schub.connector.riot.business.services.MatchEnrichmentService.liste;
import static schultz.thomas.schub.connector.riot.business.services.MatchEnrichmentService.objet;

// Même forme que la timeline, pour que ses analyses tournent dessus. Mesuré sur 275 parties : 41 ko sur disque contre 169.
public final class TimelineDigest {

    private static final List<String> CADRE = List.of("participantId", "totalGold", "currentGold", "xp", "level",
            "minionsKilled", "jungleMinionsKilled", "position");
    private static final List<String> DEGATS = List.of("totalDamageDoneToChampions", "totalDamageTaken");
    // victimDamage* et victimTeamfightDamage* font la moitié du poids d'une timeline.
    private static final List<String> KILL = List.of("type", "timestamp", "killerId", "victimId",
            "assistingParticipantIds", "position", "bounty", "shutdownBounty", "killStreakLength");
    private static final Set<String> ECARTES = Set.of("LEVEL_UP", "ITEM_DESTROYED");

    private TimelineDigest() {
    }

    public static Document of(Map<String, Object> raw) {
        Map<String, Object> info = objet(raw, "info");
        return new Document("metadata", raw.get("metadata"))
                .append("info", new Document("frameInterval", info.get("frameInterval"))
                        .append("participants", info.get("participants"))
                        .append("frames", liste(info, "frames").stream()
                                .map(frame -> image(enObjet(frame)))
                                .toList()));
    }

    private static Document image(Map<String, Object> frame) {
        Document joueurs = new Document();
        objet(frame, "participantFrames").forEach((id, brut) -> {
            Map<String, Object> joueur = enObjet(brut);
            joueurs.append(id, garde(joueur, CADRE).append("damageStats", garde(objet(joueur, "damageStats"), DEGATS)));
        });
        return new Document("timestamp", frame.get("timestamp"))
                .append("participantFrames", joueurs)
                .append("events", liste(frame, "events").stream()
                        .map(MatchEnrichmentService::enObjet)
                        .filter(event -> !ECARTES.contains(String.valueOf(event.get("type"))))
                        .map(event -> "CHAMPION_KILL".equals(event.get("type")) ? garde(event, KILL) : new Document(event))
                        .toList());
    }

    private static Document garde(Map<String, Object> source, List<String> champs) {
        Document garde = new Document();
        champs.stream().filter(source::containsKey).forEach(champ -> garde.append(champ, source.get(champ)));
        return garde;
    }
}
