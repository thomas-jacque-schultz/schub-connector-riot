package schultz.thomas.schub.connector.riot.business.search;

final class EditDistance {

    private EditDistance() {
    }

    static int between(String gauche, String droite) {
        if (gauche.equals(droite)) {
            return 0;
        }
        if (gauche.isEmpty() || droite.isEmpty()) {
            return Math.max(gauche.length(), droite.length());
        }

        int[][] distances = new int[gauche.length() + 1][droite.length() + 1];
        for (int ligne = 0; ligne <= gauche.length(); ligne++) {
            distances[ligne][0] = ligne;
        }
        for (int colonne = 0; colonne <= droite.length(); colonne++) {
            distances[0][colonne] = colonne;
        }

        for (int ligne = 1; ligne <= gauche.length(); ligne++) {
            for (int colonne = 1; colonne <= droite.length(); colonne++) {
                int cout = gauche.charAt(ligne - 1) == droite.charAt(colonne - 1) ? 0 : 1;
                int distance = Math.min(distances[ligne - 1][colonne - 1] + cout,
                        Math.min(distances[ligne - 1][colonne] + 1, distances[ligne][colonne - 1] + 1));
                if (ligne > 1 && colonne > 1
                        && gauche.charAt(ligne - 1) == droite.charAt(colonne - 2)
                        && gauche.charAt(ligne - 2) == droite.charAt(colonne - 1)) {
                    distance = Math.min(distance, distances[ligne - 2][colonne - 2] + 1);
                }
                distances[ligne][colonne] = distance;
            }
        }
        return distances[gauche.length()][droite.length()];
    }
}
