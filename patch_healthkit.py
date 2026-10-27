#!/usr/bin/env python3
"""
Autorise HealthKit dans SideSign (le module de signature de SideStore) pour les identifiants Apple gratuits.

Apple permet HealthKit aux comptes gratuits (colonne « Apple Developer » du tableau des capacités iOS), mais la liste
de SideSign l'oublie : SideStore retire alors le droit com.apple.developer.healthkit en re-signant l'app, et iOS
répond « Missing com.apple.developer.healthkit entitlement ». Le patch ajoute la capacité HealthKit (HK421J6T7P)
aux fonctions activées sur l'identifiant d'app et garde les deux droits HealthKit à la signature.

Usage : python3 patch_healthkit.py <dossier SideStore>
"""
import pathlib
import sys

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")
models = root / "Dependencies/SideSign/Sources/Models"


def patch(path: pathlib.Path, edits):
    text = path.read_text()
    for old, new in edits:
        if text.count(old) != 1:
            sys.exit(f"{path.name} : repère introuvable (ou en double), SideSign a changé : {old!r}")
        text = text.replace(old, new)
    path.write_text(text)


patch(models / "Feature.swift", [
    # droit HealthKit → capacité HealthKit de l'identifiant d'app
    ("    public init?(entitlement: Entitlement) {\n",
     "    public init?(entitlement: Entitlement) {\n"
     "        if entitlement.rawValue == \"com.apple.developer.healthkit\" { self = .healthKit; return }\n"),
    ('    static let increasedDebuggingMemoryLimit: Feature   = "increasedDebuggingMemoryLimit"\n',
     '    static let increasedDebuggingMemoryLimit: Feature   = "increasedDebuggingMemoryLimit"\n'
     '    static let healthKit: Feature                       = "HK421J6T7P"\n'),
    ("    static let freeFeatures: Set<Feature> = [\n        .appGroups,\n",
     "    static let freeFeatures: Set<Feature> = [\n        .healthKit,\n        .appGroups,\n"),
    ("    static let paidFeatures: Set<Feature> = [\n        .appGroups,\n",
     "    static let paidFeatures: Set<Feature> = [\n        .healthKit,\n        .appGroups,\n"),
])

patch(models / "Entitlement.swift", [
    # droits gardés à la signature avec un compte gratuit
    ("    static let freeEntitlements: Set<Entitlement> = [\n        .appGroups,\n",
     "    static let freeEntitlements: Set<Entitlement> = [\n        .appGroups,\n"
     "        \"com.apple.developer.healthkit\",\n        \"com.apple.developer.healthkit.access\",\n"),
])

print("SideSign : HealthKit autorisé")
