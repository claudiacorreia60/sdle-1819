# Índice

- [Índice](#%C3%ADndice)
- [Central](#central)
    - [Estrutura](#estrutura)
      - [Registo de um user](#registo-de-um-user)
      - [Login de um user](#login-de-um-user)
      - [Logout de um superuser](#logout-de-um-superuser)
      - [Atribuição de estado de superuser](#atribui%C3%A7%C3%A3o-de-estado-de-superuser)
- [User](#user)
    - [Estrutura](#estrutura-1)
    - [Ações](#a%C3%A7%C3%B5es)
      - [Transformação em superuser](#transforma%C3%A7%C3%A3o-em-superuser)
      - [Registo](#registo)
      - [Login](#login)
  - [Followee](#followee)
      - [Login](#login-1)
      - [Logout](#logout)
      - [Subscribe](#subscribe)
      - [Posts](#posts)
  - [Follower](#follower)
      - [Login](#login-2)
      - [Login do followee](#login-do-followee)
      - [Logout do followee:](#logout-do-followee)
      - [Subscribe](#subscribe-1)
      - [Posts](#posts-1)
  - [Superuser](#superuser)
      - [Logout](#logout-1)
- [Convenções](#conven%C3%A7%C3%B5es)
- [Tecnologias](#tecnologias)
- [To do](#to-do)
- [Extras](#extras)

# Central

### Estrutura
- os IPs dos superusers que existem (assinala se estão online ou não)
- os users que estão registados

#### Registo de um user
- Ao receber a mensagem do tipo ```SIGNUP```:
	- Se o username ainda não existir:
	```
	TYPE: ACK
	```

	- Se o username já existir:
	```
	TYPE: NACK
	```

#### Login de um user
- Ao receber a mensagem do tipo ```LOGIN```:
	- Caso as credenciais estejam erradas é-lhe enviada a seguinte mensagem:
	```
	TYPE: NACK
	```

	- Caso as credenciais estejam corretas é-lhe atribuído um superuser aleatoriamente
	```
	SUPERUSER: <superuser_private_group>
	TYPE: SUPERUSER
	```

- Caso se trate de um superuser, é atualizada a estrutura com informação de que este está ```ONLINE```

#### Logout de um superuser
- Ao receber a mensagem do tipo ```LOGGED OUT```:
	- Atualiza a estrutura com informação de que este está ```OFFLINE```
	- Escolhe aleatoriamente um superuser
	- Envia para o grupo daquele superuser o novo superuser que escolheu

```
SUPERUSER: <superuser_private_group>
TYPE: SUPERUSER_UPDATE
```

- Envia ao superuser um ack

```
TYPE: DISCONNECT
```

#### Atribuição de estado de superuser
- Quando não existe nenhum superuser, a central deve nomear um dos users como superuser
	1. Seleciona um dos users existentes
	2. Envia uma mensagem ao superuser:
	```
	TYPE: PROMOTION
	```
	3. Espera um _timeout_ e:
		- se receber um ACK do novo superuser atualiza a estrutura
		- se não receber nenhum ACK, volta ao ponto 1.


# User
	
### Estrutura
- o seu username
- a sua password
- os seus posts
- os followees e os seus posts (posts + estado (UPDATED ou OUTDATED))
- o seu superuser
- se é superuser ou não
- private group da central

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
	- melhor hardware (?)
- Atualiza a sua estrutura, indicando que é um superuser
- Avisa a central de que passou a ser um superuser:

```
TYPE: SUPERUSER
```

#### Registo
- Conecta-se à central e faz join ao grupo da central
- Manda uma mensagem do tipo ```SIGNUP``` para a central

```
TYPE: SIGNUP
PASSWORD: <password>
```

#### Login
- Conecta-se à central e faz join ao grupo da central
- Envia uma mensagem de login à central
```
TYPE: LOGIN
PASSWORD: <password>
```
- Recebe mensagem com o IP do seu superuser (```SUPERUSER```) e atualiza a estrutura

## Followee
	
#### Login
- Faz join ao seu grupo
- Caso não tenha informação local, envia uma mensagem para o grupo a pedir os dados dele:

```
TYPE: DATA REQUEST
```

#### Logout
- Envia mensagem de logout para o grupo, com os seus followees:

```
TYPE: LOGGED OUT
FOLLOWEES: <followees_list>
```

#### Subscribe
- Recebe mensagem de ```SUBSCRIBE``` de um user
- Se a mensagem tiver vindo do grupo dele, envia uma mensagem ao novo follower:

```
TYPE: SUBSCRIPTION
POSTS: <posts list>
```

#### Posts
- Recebe mensagem a pedir posts (```UPDATE```) a partir de um certo identificador

Envia uma mensagem com os respetivos posts:

```
TYPE: POSTS
POSTS: <posts list>
```

- Envia uma mensagem com uma lista composta pelo único post:

```
TYPE: POST
POSTS: <posts list>
```


## Follower

#### Login
- Assinala os posts de todos os followees como ```OUTDATED```
- Junta-se aos grupos dos followees
- Envia mensagem a pedir posts a todos os followees (ou followers caso o próprio followee não esteja online)

```
TYPE: UPDATE
LAST_POST_ID: <last_post_id>
```

#### Login do followee
- Recebe mensagem de membership:
	Caso os posts do followee estejam ```OUTDATED```, envia uma mensagem
	```
	TYPE: UPDATE
	LAST_POST_ID: <last_post_id>
	```

#### Logout do followee:
- Recebe mensagem de logout do followee e atualiza a estrutura com ```OFFLINE```

#### Subscribe
- Caso queira fazer uma subscrição:
	1. Junta-se ao grupo do followee
	2. Espera por uma mensagem de membership para ver se o followee está online
	3. Se o followee estiver online fala diretamente com ele, senão envia a mensagem para um dos followers que estiver online
	```
	TYPE: SUBSCRIBE
	```

- Caso tenha recebido uma mensagem de subscribe que não seja do grupo dele, então envia uma mensagem com posts desse followee e com o estado dos posts que tem:

```
TYPE: POSTS
POSTS: <posts list>
STATE: OUTDATED/UPDATED
```

#### Posts
- Recebe uma mensagem de ```POST``` de um followee e guarda
- Recebe uma mensagem de ```POSTS``` de um followee, atualiza a estrutura e assinala os posts como ```UPDATED```
- Recebe uma mensagem de ```POSTS``` de um follower, atualiza a estrutura e assinala os posts como ```UPDATED``` ou ```OUTDATED```, mediante o que  follower tiver dito na mensagem


## Superuser

#### Logout
- Envia mensagem de logout para a central:

```
TYPE: LOGGED OUT
```

- Aguarda o ```DISCONNECT``` da central

# Convenções

- O grupo da central será: `centralGroup`
- O grupo de cada user será: `<username>Group`
- O grupo de cada superuser será `<username>SuperGroup`

# Tecnologias


- Spread
	- Toolkit robusto, poderoso e simples que permite desenvolver arquiteturas distribuídas.
	- Encapsula vários aspetos de sistemas distribuídos assíncronos permitindo focar apenas nos diferentes componentes da aplicação.
	- Permite abstrair a noção de círculo de amigos em grupos.
	- Permite unicast, multicast, multi-group multicast e chamadas scatter-gather.
	- Permite uma grande escalabilidade sem necessidade de grandes alterações arquiteturais.
	- Permite a entrega de mensagens por ordem causal de maneira simples.

# To do

- Definir o período para guardar as mensagens e quantas


# Extras

- Pesquisa:

	- ou fazer flooding de "ana" e todos os users que tiverem isso no nome respondem com o seu username
	- utilizar uma estrutura (p.e. Trie distribuida) que permita enviar os pedidos só para os users cujo username comece por "ana"

- Unfollow
