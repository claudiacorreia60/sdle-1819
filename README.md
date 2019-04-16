
# Índice
- [CENTRAL](#central)
	- [Estrutura](#estrutura)
		- [Registo de um user](#registo-de-um-user)
		- [Login de um user](#login-de-um-user)
		- [Logout de um superuser](#logout-de-um-superuser)
		- [Atribuição de estado de superuser](#atribui%C3%A7%C3%A3o-de-estado-de-superuser)
- [USER](#user)
	- [Estrutura](#estrutura-1)
	- [Ações](#a%C3%A7%C3%B5es)
		- [Transformação em superuser](#transforma%C3%A7%C3%A3o-em-superuser)
		- [Registo](#registo)
		- [Login](#login)
	- [FOLLOWEE](#followee)
		- [Login](#login-1)
		- [Logout](#logout)
		- [Subscribe](#subscribe)
		- [Posts](#posts)
	- [FOLLOWER](#follower)
		- [Login](#login-2)
		- [Login do followee](#login-do-followee)
		- [Logout do followee:](#logout-do-followee)
		- [Subscribe](#subscribe-1)
		- [Posts](#posts-1)
	- [SUPERUSER](#superuser)
		- [Logout](#logout-1)
- [Implementação](#implementa%C3%A7%C3%A3o)
	- [Flooding das mensagens](#flooding-das-mensagens)
		- [Tipos de mensagens que necessitam de flooding](#tipos-de-mensagens-que-necessitam-de-flooding)
		- [Como fazer flooding](#como-fazer-flooding)
- [To do](#to-do)
- [Questões](#quest%C3%B5es)
- [Extras](#extras)

# CENTRAL

### Estrutura
- os IPs dos superusers que existem (assinala se estão online ou não)
- os users que estão registados

#### Registo de um user
- Ao receber a mensagem do tipo ```SIGNUP```:
	- Se o username ainda não existir:
	```
	USERNAME: central
	IP: <ip>
	TYPE: ACK
	```

	- Se o username já existir:
	```
	USERNAME: central
	IP: <ip>
	TYPE: NACK
	```

#### Login de um user
- Ao receber a mensagem do tipo ```LOGIN```:
	- Caso as credenciais estejam erradas é-lhe enviada a seguinte mensagem:
	```
	USERNAME: central
	IP: <ip>
	TYPE: NACK
	```

	- Caso as credenciais estejam corretas é-lhe atribuído um superuser aleatoriamente
	```
	USERNAME: central
	IP: <superuser_ip>
	TYPE: SUPERUSER
	```

- Caso se trate de um superuser, é atualizada a estrutura com informação de que este está ```ONLINE```

#### Logout de um superuser
- Atualiza a estrutura com informação de que este está ```OFFLINE```
- Escolhe aleatoriamente um superuser
- Faz flooding de uma mensagem de reatribuição de superuser

```
USERNAME: central
IP: <superuser_ip>
TYPE: SUPERUSER_UPDATE
UUID: <uuid>
```

#### Atribuição de estado de superuser
- Quando não existe nenhum superuser, a central deve nomear um dos users como superuser
	1. Seleciona um dos users existentes
	2. Envia uma mensagem ao superuser:
	```
	USERNAME: central
	IP: <ip>
	TYPE: PROMOTION
	```
	3. Espera um _timeout_ e:
		- se receber um ACK do novo superuser atualiza a estrutura
		- se não receber nenhum ACK, volta ao ponto 1.


# USER
	
### Estrutura
- o seu username
- a sua password
- os seus posts
- os posts dos seus followees
- os followees
- os followers
- os neighbours (caso seja um superuser)
- o seu superuser
- se é superuser ou não
- IP da central

### Ações
- Registo
- Login
- Logout
- Post
- Subscribe

#### Transformação em superuser
- Transforma-se quando:
	- tem mais de x followers
	- está ativo há mais x tempo (_uptime_)
- Atualiza a sua estrutura, indicando que é um superuser
- Avisa a central de que passou a ser um superuser:

```
IP: <superuser_ip>
TYPE: SUPERUSER
```

#### Registo
- Manda uma mensagem do tipo ```SIGNUP``` para a central

```
USERNAME: <username>
IP: <ip>
TYPE: SIGNUP
PASSWORD: <password>
```

#### Login
- Envia uma mensagem de login à central
```
USERNAME: <username>
IP: <ip>
TYPE: LOGIN
PASSWORD: <password>
```
- Recebe mensagem com o IP do seu superuser (```SUPERUSER```) e atualiza a estrutura

## FOLLOWEE
	
#### Login
- Envia mensagem de login para os followers:

```
USERNAME: <username>
IP: <ip>
TYPE: LOGGED IN
```

#### Logout
- Envia mensagem de logout para os followers:

```
USERNAME: <username>
TYPE: LOGGED OUT
```

#### Subscribe
- Recebe mensagem de ```SUBSCRIPTION_OTHER``` de um follower

Envia uma mensagem de login aos novos followers:

```
USERNAME: <username>
IP: <ip>
TYPE: LOGGED IN
```

- Recebe mensagem de ```SUBSCRIPTION``` de um user, cujo followee seja ele

Envia uma mensagem de subscribe aos novos followers:

```
USERNAME: <username>
IP: <ip>
TYPE: SUBSCRIBE
FOLLOWERS: <followers list>
POSTS: <posts list>
```

#### Posts
- Recebe mensagem a pedir posts (```UPDATE```) a partir de um certo identificador

Envia uma mensagem com os respetivos posts:

```
USERNAME: <username>
TYPE: POSTS
POSTS: <posts list>
```

- Envia uma mensagem com uma lista composta pelo único post:

```
USERNAME: <username>
TYPE: POST
POSTS: <posts list>
```


## FOLLOWER

#### Login
- Atualiza as estruturas: 
	1. Coloca o estado de todos os followees a ```UNKNOWN```
	2. Assinala os posts como ```OUTDATED```

- Envia mensagem a todos os followees

```
USERNAME: <username>
IP: <ip>
TYPE: UPDATE
LAST_POST_ID: <last_post_id>
```

#### Login do followee
- Recebe mensagem de login do followee:
	1. Atualiza a estrutura com ```ONLINE``` e com o novo IP
	2. Caso os posts do followee estejam ```OUTDATED```, envia uma mensagem
	```
	USERNAME: <username>
	IP: <ip>
	TYPE: UPDATE
	LAST_POST_ID: <last_post_id>
	```

#### Logout do followee:
- Recebe mensagem de logout do followee e atualiza a estrutura com ```OFFLINE```

#### Subscribe
- Faz subscribe de um user
	1. Propaga o pedido pela rede

	```
	USERNAME: <username>
	IP: <ip>
	TYPE: SUBSCRIPTION
	FOLLOWEE: <username>
	UUID: <uuid>
	```

	2. Processa as mensagem que recebe durante um _timeout_. As mensagens podem ser dos tipos:
		- OFFLINE
		- ONLINE
		- UNKNOWN
		- SUBSCRIBE
	3. Caso tenha recebido um ```SUBSCRIBE```, atualiza as estruturas com os posts e a lista de followers e ignora as restantes mensagens (o _timeout_ é interrompido)
	4. Caso tenha recebido um ```OFFLINE```, interrompe o _timeout_ e envia uma mensagem a esse mesmo follower:

	```
	USERNAME: <username>
	IP: <ip>
	TYPE: SUBSCRIPTION_OTHER
	FOLLOWEE: <username>
	```

	5. Ao terminar o _timeout_:
		1. Caso tenha pelo menos uma mensagem ```ONLINE```, envia uma mensagem a esse mesmo follower:

		```
		USERNAME: <username>
		IP: <ip>
		TYPE: SUBSCRIBE_OTHER
		FOLLOWEE: <username>
		```

		2. Caso contrário é apresentada uma mensagem ao utilizador de erro.

	6. Ao receber a resposta da subscrição:
		1. Caso seja ```SUBSCRIBE``` atualizo a estrutura e assinalo os posts como ```UPDATED```
		2. Caso seja ```SUBSCRIPTION_OTHER``` atualizo a estrutura e assinalo os posts como ```OUTDATED```

- Recebe pedidos de ```SUBSCRIPTION``` cujo followee especificado na mensagem não seja ele, mas seja um dos seus followees
	1. Envia uma mensagem ao user que fez o pedido, a dizer qual o estado do followee

	```
	USERNAME: <username>
	IP: <ip>
	TYPE: <followee_status>
	FOLLOWEE: <username>
	```

- Recebe pedidos de ```SUBSCRIBE_OTHER```
	1. Envia uma mensagem ao user

	```
	USERNAME: <follower_username>
	TYPE: SUBSCRIBE_OTHER
	FOLLOWERS: <followers list>
	POSTS: <posts list>
	FOLLOWEE: <followee_username>
	```

	2. Quando o followee ficar online, vai informá-lo da subscrição

	```
	USERNAME: <username>
	IP: <follower_ip>
	TYPE: SUBSCRIPTION_OTHER
	FOLLOWER: <follower_username>
	```

#### Posts
- Recebe uma mensagem de ```POST``` de um followee e guarda
- Recebe uma mensagem de ```POSTS``` de um followee, atualiza a estrutura e assinala os posts como ```UPDATED```


## SUPERUSER

#### Logout
- Envia mensagem de logout para a central:

```
USERNAME: <username>
TYPE: LOGGED OUT
```


# Implementação

## Flooding das mensagens

#### Tipos de mensagens que necessitam de flooding
- ```SUBSCRIPTION```
- ```SUPERUSER_UPDATE```

#### Como fazer flooding
- Cada mensagem tem um UUID diferente (https://docs.oracle.com/javase/7/docs/api/java/util/UUID.html)
- O UUID das mensagens de flooding é guardado numa estrutura
- Eu só propago as mensagens cujo UUID não esteja na minha estrutura, ou seja, que não propaguei antes.


# To do

- Definir o período para guardar as mensagens
- Definir o período para limpar a minha estrutura de mensagens de flooding
- Atualizar a lista de followers
- IPs mudarem
- Respeitar a causalidade (usar os escalares de lamport)


# Questões

- Podemos assumir que não há crash stop?


# Extras

- Pesquisa:

	- ou fazer flooding de "ana" e todos os users que tiverem isso no nome respondem com o seu username
	- utilizar uma estrutura (p.e. Trie distribuida) que permita enviar os pedidos só para os users cujo username comece por "ana"

- Unfollow
