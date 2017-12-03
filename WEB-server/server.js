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
fileUpload = require('express-fileupload'),
GoogleMapsAPI = require('googlemaps');

var publicConfig = {
  key: 'AIzaSyBVewWY6ssWSMcda5R-ODP8KISHSO8Sj0c',
  stagger_time:       1000, // for elevationPath 
  encode_polylines:   false,
  secure:             true, // use https 
  proxy:              'http://127.0.0.1:9999' // optional, set a proxy for HTTP requests 
};
var gmAPI = new GoogleMapsAPI(publicConfig);

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
app.use(bodyParser.urlencoded({limit: '50mb', extended: true }));
app.use(bodyParser.json({limit: '50mb'}));
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

function getMapURL(markersStr) {
	
	var params = {
		size: '500x400',
		maptype: 'roadmap',
		markers: markersStr,
		style: [
			{
				feature: 'road',
				element: 'all',
				rules: {
					hue: '0x00ff00'
				}
			}
		]
	};

	return gmAPI.staticMap(params); // return static map URL

}

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
	
	var done = false;
	var timeoutMsg = "CMC not online";
	//console.log(req.body);
	
	if (!req.body.faceID) {
		res.render('queryFaceMovements.jade',
		{
			params: { title: 'Third Eye Surveillance System - Query Face Movements' }
		});
		done = true;
		return;
	}
	
	try {
	
	// create conection
	amqp.connect('amqp://' + rabbitMQserverAddress, function(err, conn) {
		
		// create channel
		conn.createChannel(function(err, ch) {
			
			// create exchange to CMC
			ch.assertExchange('CMC_DIRECT', 'direct', {durable: false})

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
				
				var tag = req.body.server;
				if (tag == 'cmb') {
					tag = tag.concat(req.body.server_cmb);
					timeoutMsg = "CMB" +  req.body.server_cmb + " not online";
				}
				//console.log(tag);
				
				try {
					ch.publish('CMC_DIRECT', tag, new Buffer(JSON.stringify(jsonMessage)), { replyTo: q.queue });
				}
				catch (err) {
					return;
				}
				
				ch.consume(q.queue, function(msg) {
				
					//console.log(' [.] Got %s', msg.content.toString());
					var jsonResponse = JSON.parse(msg.content.toString());
					
					var markers = [];
					for (var i = 0; i < jsonResponse["dataSize"]; ++i) {
						var data = jsonResponse["data"][i];
						
						var marker = {};
						marker["location"] = data["coordX"] + "," + data["coordY"];
						markers.push(marker);
					}
					
					var markersNow = JSON.parse(JSON.stringify(markers));
					markersNow[jsonResponse["dataIndex"]]["label"] = "A";
					
					var mapURL = getMapURL(markersNow);
					
					res.render('queryFaceMovements.jade',
					{
						params: { title: 'Third Eye Surveillance System - Query Face Movements',
						showResponse: true, response: jsonResponse["response"], match: jsonResponse["match"],
						databaseID: jsonResponse["databaseID"], dataSize: jsonResponse["dataSize"],
						data: jsonResponse["data"], dataIndex: jsonResponse["dataIndex"], mapURL: mapURL,
						markers: markers }
					});
					
					setTimeout(function() { conn.close(); return }, 500);
					done = true;
				}, {noAck: true});
				
			});

		});
		
		setTimeout(function() {
			
			if (!done) {
				conn.close();
				res.render('queryFaceMovements.jade',
				{
					params: { title: 'Third Eye Surveillance System - Query Face Movements',
					showResponse: true, response: timeoutMsg }
				});
			}
			return 
		
		}, 2000);
		
	});
	
	}
	catch(err) {
		res.render('queryFaceMovements.jade',
		{
			params: { title: 'Third Eye Surveillance System - Query Face Movements' }
		});
		done = true;
		return;
	}
	
});

// POST localhost:3000/queryFaceMovements/next
app.post('/queryFaceMovements/next', function(req, res){
	
	console.log(" [x] POST /queryFaceMovements/next");
	
	var parsedParams = JSON.parse(req.body.params);
	
	if (!req.body || !req.body.params) {
		res.render('queryFaceMovements.jade',
		{
			params: { title: 'Third Eye Surveillance System - Query Face Movements' }
		});
		done = true;
		return;
		
	} else {
	
		var dataIndex = parsedParams["dataIndex"];
		
		if (req.body.indiceAccion == "Prev") {
			dataIndex = dataIndex - 1;
		} else {
			dataIndex = dataIndex + 1;
		}
		
		var n = parsedParams["dataSize"];
		dataIndex = ((dataIndex%n)+n)%n;
		
		var markersNow = JSON.parse(JSON.stringify(parsedParams["markers"]));
		markersNow[dataIndex]["label"] = "A";
		var mapURL = getMapURL(markersNow);
	
		res.render('queryFaceMovements.jade',
		{
			params: { title: 'Third Eye Surveillance System - Query Face Movements',
			showResponse: true, response: parsedParams["response"], match: parsedParams["match"],
			databaseID: parsedParams["databaseID"], dataSize: parsedParams["dataSize"],
			data: parsedParams["data"], dataIndex: dataIndex, mapURL: mapURL,
			markers: parsedParams["markers"] }
		});
	
	}
	
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
	
	var done = false;
	
	if (!req.files || !req.files.displayImage) {
		res.render('queryFace.jade',
		{
			params: { title: 'Third Eye Surveillance System - Query Face' }
		});
		done = true;
		return;
	}
	
	// create conection
	amqp.connect('amqp://' + rabbitMQserverAddress, function(err, conn) {
	
		// create channel
		conn.createChannel(function(err, ch) {
			// create exchange to CMC
			ch.assertExchange('CMC_DIRECT', 'direct', {durable: false})

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
				
				ch.publish('CMC_DIRECT', 'cmc', new Buffer(JSON.stringify(jsonMessage)), { replyTo: q.queue });
				
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
					done = true;
				}, {noAck: true});
				
			});

		});
		
		setTimeout(function() {
			
			if (!done) {
				conn.close();
				res.render('queryFace.jade',
				{
					params: { title: 'Third Eye Surveillance System - Query Face',
					showResponse: true, response: "ERROR: CMC not online" }
				});
			}
			return 
		
		}, 2000);

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
	
	var done = false;
	
	if (!req.files || !req.files.displayImage) {
		res.render('uploadFace.jade',
		{
			params: { title: 'Third Eye Surveillance System - Upload Face' }
		});
		done = true;
		return;
	}
	
	// create conection
	amqp.connect('amqp://' + rabbitMQserverAddress, function(err, conn) {
	
		// create channel
		conn.createChannel(function(err, ch) {
			// create exchange to CMC
			ch.assertExchange('CMC_DIRECT', 'direct', {durable: false})

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
				
				ch.publish('CMC_DIRECT', 'cmc', new Buffer(JSON.stringify(jsonMessage)), { replyTo: q.queue });
				
				ch.consume(q.queue, function(msg) {
					
					console.log(' [.] Got %s', msg.content.toString());
					var jsonResponse = JSON.parse(msg.content.toString());
					var textResponse = jsonResponse["response"];
					
					res.render('uploadFace.jade',
					{
						params: { title: 'Third Eye Surveillance System - Upload Face', showResponse: true, response: textResponse }
					});
					
					setTimeout(function() { conn.close(); return }, 500);
					done = true;
				}, {noAck: true});
				
			});

		});
		
		setTimeout(function() {
		
			if (!done) {
				conn.close();
				res.render('uploadFace.jade',
				{
					params: { title: 'Third Eye Surveillance System - Upload Face',
					showResponse: true, response: "ERROR: CMC not online" }
				});
			}
			return 
		
		}, 2000);

	});

});

// **********************************************************

// start server
http.createServer(app).listen(app.get('port'), function(){
	console.log(" [*] RabbitMQ + Node.js server running!");
});