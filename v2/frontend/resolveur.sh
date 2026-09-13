#!/bin/sh
# Écrit la directive `resolver` de nginx à partir du DNS du conteneur.
#
# nginx résout les noms de `proxy_pass` AU DÉMARRAGE lorsqu'ils sont écrits en
# dur. Un nom injoignable à cet instant — un Service pas encore créé, un
# redémarrage pendant une bascule, ou simplement l'image lancée hors du
# cluster — et nginx refuse de démarrer : « host not found in upstream ». Le
# portail entier tombait alors, y compris les pages statiques, qui n'ont
# pourtant besoin d'aucune API.
#
# Avec une VARIABLE dans `proxy_pass`, nginx diffère la résolution à la
# requête. Il lui faut pour cela un résolveur, et celui-ci n'est connu qu'ici :
# il vit dans /etc/resolv.conf, écrit par le moteur de conteneurs.
set -e
serveurs=$(awk '/^nameserver/ { printf "%s%s", sep, $2; sep=" " }' /etc/resolv.conf)
# 127.0.0.11 est le résolveur intégré de Docker : le dernier filet si
# /etc/resolv.conf est vide ou illisible.
[ -n "$serveurs" ] || serveurs="127.0.0.11"
echo "resolver $serveurs valid=10s ipv6=off;" > /etc/nginx/conf.d/00-resolveur.conf

# Échouer bruyamment. Sans résolveur, nginx démarre quand même — la variable
# dans proxy_pass ne se résout qu'à la requête — et rend 502 sur toute l'API
# sans rien dire. Mieux vaut ne pas démarrer du tout : le point d'entrée
# officiel tourne sous `set -e` et s'arrêtera ici.
test -s /etc/nginx/conf.d/00-resolveur.conf
echo "resolveur nginx : $serveurs"
