package app.superhealth.ui

/** 3.9 : questionnaire en aperçu, générateur de programme nutrition réécrit en étapes, base d'aliments du serveur. */
object Strings34 {
    val map = mapOf(
        "Aperçu, rien n'est modifié" to "Preview, nothing is changed",
        "Aperçu · rien n'est modifié" to "Preview · nothing is changed",
        "Fermer l'aperçu" to "Close preview",
        // mode manuel du guide nutrition
        "Préparations et pros : vous fixez calories et macros vous-même, jour par jour et semaine par semaine. Aucun ajustement automatique." to
            "Contest prep and pros: you set calories and macros yourself, day by day and week by week. No automatic adjustment.",
        "Votre semaine type" to "Your typical week",
        "Elle s'applique chaque semaine tant qu'aucun programme ne la remplace. Une cible = calories + protéines, lipides et glucides." to
            "It applies every week unless a program replaces it. A target = calories + protein, fat and carbs.",
        "Facultatif : laissez vide pour garder les repères officiels. Une semaine du programme pourra les remplacer." to
            "Optional: leave empty to keep official guidelines. A program week can override them.",
        "Repères :\n• Eau : environ 35 ml par kg, plus les jours d'entraînement (la sueur fait perdre 0,5 à 2 L par heure).\n• Sodium : moins de 2 000 mg par jour hors sueur (OMS).\n• Potassium : au moins 3 500 mg par jour.\n• Fibres : 25 à 38 g par jour." to
            "Guidelines:\n• Water: about 35 ml per kg, more on training days (sweat loses 0.5 to 2 L per hour).\n• Sodium: under 2,000 mg per day excluding sweat (WHO).\n• Potassium: at least 3,500 mg per day.\n• Fiber: 25 to 38 g per day.",
        "Facultatif : pour faire évoluer vos cibles d'une semaine à l'autre (sèche, prise de masse, préparation). Tout reste modifiable ensuite dans Nutrition > Plan." to
            "Optional: to change your targets from week to week (cut, bulk, contest prep). Everything stays editable later in Nutrition > Plan.",
        "Non, répéter ma semaine type" to "No, repeat my typical week",
        "Oui, construire un programme" to "Yes, build a program",
        "La semaine réglée plus tôt revient chaque semaine" to "The week set earlier repeats every week",
        "Calories et macros qui évoluent semaine après semaine, en quelques réglages" to "Calories and macros that change week after week, in a few settings",
        "Une cible pour les jours d'entraînement (ON), une pour le repos (OFF). Touchez un jour pour le basculer, puis une carte pour régler sa cible." to
            "One target for training days (ON), one for rest days (OFF). Tap a day to switch it, then a card to set its target.",
        "Une cible différente par jour : touchez un jour pour la régler." to "A different target for each day: tap a day to set it.",
        // générateur de semaines
        "Quel type de programme ?" to "What kind of program?",
        "Un bloc de semaines" to "A block of weeks",
        "Prise de masse, maintien, sèche ou reverse : les calories changent d'un même pas chaque semaine." to
            "Bulk, maintenance, cut or reverse: calories change by the same step every week.",
        "Une préparation complète" to "A full contest prep",
        "Pour une compétition ou un shooting : sèche avec recharges et pauses diète, semaine de pic, puis retour au maintien." to
            "For a competition or a photo shoot: cut with refeeds and diet breaks, peak week, then back to maintenance.",
        "Quand commencer ?" to "When to start?",
        "Votre dépense estimée (maintien) : %1\$s %2\$s par jour. Les valeurs proposées en partent." to
            "Your estimated expenditure (maintenance): %1\$s %2\$s per day. Suggested values start from it.",
        "Quelle phase ?" to "Which phase?",
        "Léger surplus pour prendre du muscle en limitant le gras : environ 250 kcal au-dessus du maintien." to
            "Small surplus to build muscle while limiting fat: about 250 kcal above maintenance.",
        "Déficit pour perdre du gras : environ 500 kcal sous le maintien, protéines plus hautes pour garder le muscle." to
            "Deficit to lose fat: about 500 kcal below maintenance, higher protein to keep muscle.",
        "Après une sèche : les calories remontent pas à pas vers le maintien pour limiter la reprise de gras." to
            "After a cut: calories climb step by step back to maintenance to limit fat regain.",
        "Calories stables au niveau du maintien." to "Calories steady at maintenance.",
        "1re semaine" to "Week 1",
        "%1\$s/j" to "%1\$s/d",
        "Puis, chaque semaine" to "Then, every week",
        "Ne pas changer" to "Keep the same",
        "Baisse par semaine" to "Drop per week",
        "Hausse par semaine" to "Rise per week",
        "Macros" to "Macros",
        "g/kg" to "g/kg",
        "% kcal" to "% kcal",
        "g/kg maigre" to "g/kg lean",
        "Protéines : grammes par kg de poids (%1\$s kg), soit %2\$s g par jour." to "Protein: grams per kg of body weight (%1\$s kg), i.e. %2\$s g per day.",
        "Lipides : part des calories, soit %1\$s g par jour la 1re semaine." to "Fat: share of calories, i.e. %1\$s g per day in week 1.",
        "Les glucides complètent le reste des calories." to "Carbs fill the rest of the calories.",
        "Jours de recharge (facultatif)" to "Refeed days (optional)",
        "Ces jours-là, les calories remontent au maintien, surtout en glucides : meilleures séances et sèche plus facile à tenir." to
            "On those days, calories go back up to maintenance, mostly from carbs: better sessions and an easier cut.",
        "La sèche" to "The cut",
        "Votre maintien" to "Your maintenance",
        "La 1re semaine proposée vise environ −0,75 % du poids par semaine (repère : 0,5 à 1 %). Le maintien sert aux recharges, aux pauses diète et au reverse." to
            "The suggested week 1 aims for about −0.75% of body weight per week (guideline: 0.5 to 1%). Maintenance is used for refeeds, diet breaks and the reverse.",
        "Protéines : grammes par kg de masse maigre (poids sans le gras, ≈ %1\$s kg), soit %2\$s g par jour. Repère : 2,3 à 3,1." to
            "Protein: grams per kg of lean mass (weight without fat, ≈ %1\$s kg), i.e. %2\$s g per day. Guideline: 2.3 to 3.1.",
        "Lipides : %1\$s g par jour la 1re semaine (repère : 15 à 30 %%)." to "Fat: %1\$s g per day in week 1 (guideline: 15 to 30%%).",
        "Les glucides complètent le reste." to "Carbs fill the rest.",
        "Recharges et pauses" to "Refeeds and breaks",
        "Ces jours-là, calories au maintien, surtout en glucides." to "On those days, calories at maintenance, mostly from carbs.",
        "Une semaine de pause au maintien toutes les" to "One break week at maintenance every",
        "0 = aucune pause. Une pause diète aide à récupérer et à tenir une longue sèche." to "0 = no break. A diet break helps you recover and stick with a long cut.",
        "Après la sèche" to "After the cut",
        "Recharge en glucides les trois derniers jours, eau et sel inchangés" to "Carb load over the last three days, water and salt unchanged",
        "Reverse diet : remonter au maintien en" to "Reverse diet: back to maintenance over",
        "0 = retour direct au maintien." to "0 = straight back to maintenance.",
        "Régler l'eau et le sodium de ces semaines" to "Set water and sodium for these weeks",
        "Sinon, vos objectifs de base s'appliquent" to "Otherwise your baseline goals apply",
        "Laissez une case vide pour garder l'objectif de base." to "Leave a field empty to keep the baseline goal.",
        "Aperçu" to "Preview",
        // 3.9 : base d'aliments du serveur
        "Base SuperHealth" to "SuperHealth database",
        "En ligne · NCC, MND et USDA, cherchée en premier" to "Online · NCC, MND and USDA, searched first",
        "Recherche du code %1\$s…" to "Looking up code %1\$s…",
        "Complétez les cases pour voir le programme." to "Fill in the fields to see the program.",
    )
}
