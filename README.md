# Sistemas Distribuidos I

## Setup en Linux

### Para correr la parte de java en Linux (probado en Ubuntu)
Las dependencias son JavaCV y OpenCV, pero ya estan incluidas en el repo.
Solo es necesario instalar java 8, y algunas dependencias de OpenCV

sudo apt-get install openjdk-8-jre
sudo apt-get install openjdk-8-jdk
sudo apt-get install openjfx
sudo apt-get install build-essential
sudo apt-get install cmake git libgtk2.0-dev pkg-config libavcodec-dev libavformat-dev libswscale-dev
sudo apt-get install python-dev python-numpy libtbb2 libtbb-dev libjpeg-dev libpng-dev libtiff-dev libjasper-dev libdc1394-22-dev

Instalar intellij, el proyecto incluido en el repo ya tiene todos los targets necesarios.

### Para correr la interfaz WEB en Linux (probado en Ubuntu)
Las dependencias son node js, y los modulos que usan, pero ya estan en el repo.
Cualquier cosa se instalan con npm.

sudo apt-get install nodejs-legacy

Es necesario instalar node js y correr 'WEB-server/server.js'

## Setup en Windows

### Para correr la parte de java en Windows
Las dependencias son JavaCV y OpenCV, pero ya estan incluidas en el repo.
Solo es necesario instalar java 8 desde la pagina oficial.

Instalar intellij, el proyecto incluido en el repo ya tiene todos los targets necesarios.

### Para correr la interfaz WEB en Windows
Es necesario instalar node js y correr 'WEB-server/server.js'

### Para correr en limpio
Borrar las carpetas 'database_faces_match', 'database_faces_no_match', 'database_frames', 'database_SRE', 'database_SRPL'.
Si se quiere entrenar el reconocedor a mano desde cero, ir cargando las caras dentro de la carpeta 'training', desde la interfaz web.
Si se quiere entrenar todo de una, copiar las fotos de 'training' a las carpetas 'database_SRE' y 'database_SRPL' y asegurarse que no existan los archivos 'database_SRE/lbph_database' y 'database_SRPL/lbph_database'.
Borrar de esas carpetas las imagenes que no se quieren entrenar.
Correr el CMC, se crean de nuevo las carpetas (vacias) y se crean y entrenan las bases de datos con las imagenes que esten en sus respectivas carpetas.
Correr uno mas CMB. 
Correr una o mas SecurityCameras con el mismo id de CMB.

## Descripcion de Procesos

### SecurityCamera
Correrlo y seguir las indicaciones. Envia todos los archivos en "camera_frames_test" a su respectivo CMB por default. Sino envia una imagen o todas las imagenes dentro de una carpeta, pasado por parametro.

### CMB
Correrlo y seguir las indicaciones. Recibe de sus camaras imagenes, corre Face Detection en cada imagen y envia cada cara encontrada al CMC

### CMC
Correrlo. Recibe requests para modificar las bases de datos. Si las bases de datos no fueron creadas (el archivo 'lbph_database' en cada carpeta de base de datos), las crea y entrena con todas las caras en las respectivas carpetas.

UPDATE_FACE: el CMB le manda mensajes con caras detectadas, y la imagen original de la que salio la cara. El CMC guarda las imagenes originales en "database_frames". Corre Face Recognition sobre cada cara recibida, contra ambas bases de datos de SRE y SRPL. Divide los resultados en "database_faces_match" y "database_faces_no_match", segun el caso. Todos los nombres de archivo son ids unicos, en algun formato (ver los comentarios en el codigo fuente por ahora).

UPLOAD_FACE: la interfaz WEB envia una imagen para guardar como cara en una base de datos determinada. Ademas, envia el id de rostro (-1 si es un rostro que no esta en ninguna base). Antes de almacenar la imagen se corre Face Detection sobre la misma para normalizar todo, y solo se almacena si se detecta exactamente 1 rostro en la imagen recibida.

QUERY_FACE: la interfaz WEB envia una imagen para ver si esa cara esta en alguna base de datos. Antes de correr Face Recognition se corre Face Detection para normalizar la imagen recibida.

QUERY_FACE_MOVEMENTS: la interfaz WEB envia un id de rostro y el CMC le devuelve las coordenadas de las posiciones en las que estuvo ese rostro, y los frames originales de las camaras de seguridad que lo vieron.

### Interfaz WEB
Correrla con 'node WEB-server/server.js'

UPLOAD NEW FACE:  Se elije una imagen, y se indica a que base de caras se quiere subir. Tambien se indica si es una persona nueva (con -1) o si ya esta en la base (su id, se puede averiguar con QUERY FACE).

QUERY FACE: Se elije una imagen, y el resultado es el id de la persona y a que base pertenece (si pertenece a alguna).

QUERY FACE MOVEMENTS: Se ingresa el id de la persona, y se reciben todas las imagenes donde se encontro a esa persona, con los datos del evento.