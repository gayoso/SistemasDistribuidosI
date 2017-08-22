# Sistemas Distribuidos I

## Dependencias Java
JavaCV 1.3.3
OpenCV 3.3

### Para buildear OpenCV
sudo app-get install ant
export JAVA_HOME='path a java jdk'

### Para correr la parte de java en Linux (probado en Ubuntu)
sudo apt-get install openjdk-8-jre
sudo apt-get install openjdk-8-jdk
sudo apt-get install openjfx
sudo apt-get install build-essential
sudo apt-get install cmake git libgtk2.0-dev pkg-config libavcodec-dev libavformat-dev libswscale-dev
sudo apt-get install python-dev python-numpy libtbb2 libtbb-dev libjpeg-dev libpng-dev libtiff-dev libjasper-dev libdc1394-22-dev

Con eso y las libs que estan en el proyecto deberia andar

### Para correr la interfaz WEB (todavia no probado en Ubuntu)
sudo apt-get install nodejs-legacy

## Dependencias WEB

node js

Creo que con las cosas locales ya deberia andar

## Descripcion de Procesos

### SecurityCamera
Correrlo y seguir las indicaciones. Envia todos los archivos en "camera_frames_test" a su respectivo CMB

### CMB
Correrlo y seguir las indicaciones. Recibe de sus camaras imagenes, corre Face Detection en cada imagen y envia cada cara encontrada al CMC

### CMC
Correrlo. Recibe requests para modificar las bases de datos.

NOTA: a veces falla diciendo que no se cargo la dll de opencv cpp, a pesar de que esta en la lista en el proyecto de intellij. No se si es que hay que esperar un poco a que se cargue o que, revisar.

UPDATE_FACE: el CMB le manda mensajes con caras detectadas, y la imagen original de la que salio la cara. El CMC guarda las imagenes originales en "database_frames". Corre Face Recognition sobre cada cara recibida, contra ambas bases de datos de SRE y SRPL. Divide los resultados en "database_faces_match" y "database_faces_no_match", segun el caso. Todos los nombres de archivo son ids unicos, en algun formato (ver los comentarios en el codigo fuente por ahora).

UPLOAD_FACE: la interfaz WEB envia una imagen para guardar como cara en una base de datos determinada. Ademas, envia el id de rostro (-1 si es un rostro que no esta en ninguna base). Antes de almacenar la imagen se corre Face Detection sobre la misma para normalizar todo, y solo se almacena si se detecta exactamente 1 rostro en la imagen recibida.

QUERY_FACE: la interfaz WEB envia una imagen para ver si esa cara esta en alguna base de datos. Antes de correr Face Recognition se corre Face Detection para normalizar la imagen recibida.

QUERY_FACE_MOVEMENTS: la interfaz WEB envia un id de rostro y el CMC le devuelve las coordenadas de las posiciones en las que estuvo ese rostro, y los frames originales de las camaras de seguridad que lo vieron.
