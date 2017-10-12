// npm install amqplib
// npm install jade
// npm install body-parser

var rabbitMQserverAddress = "localhost";

var express = require('express'),
http = require('http'),
path = require('path'),
amqp = require('amqplib/callback_api'),
bodyParser = require('body-parser'),
fs = require('fs'),
fileUpload = require('express-fileupload');

// **********************************************************

// function to encode file data to base64 encoded string
function base64_encode(file) {
    // read binary data
    var bitmap = fs.readFileSync(file);
    // convert binary data to base64 encoded string
    return new Buffer(bitmap).toString('base64');
}

// function to create file from base64 encoded string
function base64_decode(base64str, file) {
    // create buffer object from base64 encoded string, it is important to tell the constructor that the string is base64 encoded
    var bitmap = new Buffer(base64str, 'base64');
    // write buffer to file
    fs.writeFileSync(file, bitmap);
    console.log('******** File created from base64 encoded string ********');
}

// **********************************************************

var app = express();
app.set('port', process.env.PORT || 3000);
app.set('views', __dirname + '/views');
app.set('view engine', 'jade');
app.use(bodyParser.urlencoded({ extended: true }));
app.use(bodyParser.json());
app.use(express.static(path.join(__dirname, 'public')));
app.use(fileUpload());

// GET localhost:3000/
app.get('/', function(req, res){
	console.log(" [x] GET /");
	
	res.render('homepage.jade',
    {
		params: { title: 'Third Eye Surveillance System - Home' }
    });
});

// GET localhost:3000/queryFaceMovements
app.get('/queryFaceMovements', function(req, res){
	console.log(" [x] GET /queryFaceMovements");
	
	res.render('queryFaceMovements.jade',
    {
		params: { title: 'Third Eye Surveillance System - Query Face Movements' }
    });
});

// POST localhost:3000/queryFaceMovements
app.post('/queryFaceMovements', function(req, res){
	console.log(" [x] POST /queryFaceMovements");
	
	if (!req.body.faceID) {
		res.render('queryFaceMovements.jade',
		{
			params: { title: 'Third Eye Surveillance System - Query Face Movements' }
		});
		return;
	}
	
	// create conection
	amqp.connect('amqp://' + rabbitMQserverAddress, function(err, conn) {
	
		// create channel
		conn.createChannel(function(err, ch) {
			// create exchange to CMC
			ch.assertExchange('CMC_FANOUT', 'fanout', {durable: false})

			// create response queue
			var queue = ch.assertQueue('', {exclusive: true}, function(err, q) {
				
				//console.log(req.files.displayImage.name);
				//console.log(req.body);

				// encoded image bytes
				var faceID = req.body.faceID;

				// jsonMessage
				var jsonMessage = {};
				jsonMessage["faceID"] = faceID;
				jsonMessage["requestType"] = 3; // QUERY FACE MOVEMENTS
				
				ch.publish('CMC_FANOUT', '', new Buffer(JSON.stringify(jsonMessage)), { replyTo: q.queue });
				
				ch.consume(q.queue, function(msg) {
				
					console.log(' [.] Got %s', msg.content.toString());
					var jsonResponse = JSON.parse(msg.content.toString());
					
					res.render('queryFaceMovements.jade',
					{
						params: { title: 'Third Eye Surveillance System - Query Face Movements',
						showResponse: true, response: jsonResponse["response"], match: jsonResponse["match"],
						databaseID: jsonResponse["databaseID"], numImages: jsonResponse["dataSize"],
						data: jsonResponse["data"], date: jsonResponse["date"] }
					});
					
					setTimeout(function() { conn.close(); return }, 500);
				}, {noAck: true});
				
			});

		});

	});

});

// GET localhost:3000/queryFace
app.get('/queryFace', function(req, res){
	console.log(" [x] GET /queryFace");
		
	res.render('queryFace.jade',
    {
		params: { title: 'Third Eye Surveillance System - Query Face' }
    });
});

// POST localhost:3000/queryFace
app.post('/queryFace', function(req, res){
	console.log(" [x] POST /queryFace");
	
	if (!req.files.displayImage) {
		res.render('queryFace.jade',
		{
			params: { title: 'Third Eye Surveillance System - Query Face' }
		});
		return;
	}
	
	// create conection
	amqp.connect('amqp://' + rabbitMQserverAddress, function(err, conn) {
	
		// create channel
		conn.createChannel(function(err, ch) {
			// create exchange to CMC
			ch.assertExchange('CMC_FANOUT', 'fanout', {durable: false})

			// create response queue
			var queue = ch.assertQueue('', {exclusive: true}, function(err, q) {
				
				//console.log(req.files.displayImage.name);
				//console.log(req.body);

				// encoded image bytes
				var base64data = new Buffer(req.files.displayImage.data).toString('base64');

				// jsonMessage
				var jsonMessage = {};
				jsonMessage["fileByte64"] = base64data;
				jsonMessage["requestType"] = 2; // QUERY FACE
				
				ch.publish('CMC_FANOUT', '', new Buffer(JSON.stringify(jsonMessage)), { replyTo: q.queue });
				
				ch.consume(q.queue, function(msg) {
				
					console.log(' [.] Got %s', msg.content.toString());
					var jsonResponse = JSON.parse(msg.content.toString());
					
					res.render('queryFace.jade',
					{
						params: { title: 'Third Eye Surveillance System - Query Face', 
						showResponse: true, response: jsonResponse["response"], match: jsonResponse["match"],
						faceID: jsonResponse["faceID"], databaseID: jsonResponse["databaseID"]}
					});
					
					setTimeout(function() { conn.close(); return }, 500);
				}, {noAck: true});
				
			});

		});

	});

});

// GET localhost:3000/uploadFace
app.get('/uploadFace', function(req, res){
	console.log(" [x] GET /uploadFace");

	res.render('uploadFace.jade',
    {
		params: { title: 'Third Eye Surveillance System - Upload Face' }
    });
});

// POST localhost:3000/uploadFace
app.post('/uploadFace', function(req, res){
	console.log(" [x] POST /uploadFace");
	
	if (!req.files.displayImage) {
		res.render('uploadFace.jade',
		{
			params: { title: 'Third Eye Surveillance System - Upload Face' }
		});
		return;
	}
	
	// create conection
	amqp.connect('amqp://' + rabbitMQserverAddress, function(err, conn) {
	
		// create channel
		conn.createChannel(function(err, ch) {
			// create exchange to CMC
			ch.assertExchange('CMC_FANOUT', 'fanout', {durable: false})

			// create response queue
			var queue = ch.assertQueue('', {exclusive: true}, function(err, q) {
				
				//console.log(req.files.displayImage.name);
				//console.log(req.body);

				// database type
				var databaseType = req.body.database;
				
				// face id
				var faceID = req.body.faceID;

				// encoded image bytes
				var base64data = new Buffer(req.files.displayImage.data).toString('base64');

				// jsonMessage
				var jsonMessage = {};
				jsonMessage["databaseID"] = databaseType;
				jsonMessage["faceID"] = faceID;
				jsonMessage["fileByte64"] = base64data;
				jsonMessage["requestType"] = 0; // UPLOAD FACE
				
				ch.publish('CMC_FANOUT', '', new Buffer(JSON.stringify(jsonMessage)), { replyTo: q.queue });
				
				ch.consume(q.queue, function(msg) {
					
					console.log(' [.] Got %s', msg.content.toString());
					var jsonResponse = JSON.parse(msg.content.toString());
					var textResponse = jsonResponse["response"];
					
					res.render('uploadFace.jade',
					{
						params: { title: 'Third Eye Surveillance System - Upload Face', showResponse: true, response: textResponse }
					});
					
					setTimeout(function() { conn.close(); return }, 500);
				}, {noAck: true});
				
			});

		});

	});

});

// **********************************************************

// start server
http.createServer(app).listen(app.get('port'), function(){
	console.log(" [*] RabbitMQ + Node.js server running!");
});