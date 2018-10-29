const functions = require('firebase-functions');
const express = require('express')

const app = express()
const port = process.env.PORT || 4000

var Busboy = require('busboy');
const path = require('path');
const os = require('os');
const fs = require('fs');
const serveIndex = require('serve-index');

// init firebase
var firebase = require("firebase");
var config = {
    apiKey: "AIzaSyBMZE8hXbscxJq1pwj97HirLzFadRCT2RQ",
    authDomain: "android-things-hand-game.firebaseapp.com",
    databaseURL: "https://android-things-hand-game.firebaseio.com",
    projectId: "android-things-hand-game",
    storageBucket: "android-things-hand-game.appspot.com",
    messagingSenderId: "549252041695"
};
firebase.initializeApp(config);

var admin = require("firebase-admin");
var serviceAccount = require("./serviceAccountKey.json");

admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
    databaseURL: "https://android-things-hand-game.firebaseio.com"
});

// Get a reference to the storage service, which is used to create references in your storage bucket
const bucket = admin.storage().bucket("android-things-hand-game.appspot.com");

const steps = {
    "welcome": {
        title: "Rock Paper Scissors - Lizard Spock",
        subtitle: "Help us make a dataset for playing Rock Paper Scissors Lizard Spock with Android Things",
        buttonTitle: "I'm ready!",
        nextStep: "rock"
    },
    "rock": {
        capture: true,
        title: "Rock",
        subtitle: "Upload a picture of your hand doing the Rock symbol: ✊ 👊 🤛 🤜",
        buttonTitle: "Upload",
        nextStep: "paper",
    },
    "paper": {
        capture: true,
        title: "Paper",
        subtitle: "Upload a picture of your hand doing the Paper symbol: 🖐️ 👋 ✋",
        buttonTitle: "Upload",
        nextStep: "scissors",
    },
    "scissors": {
        capture: true,
        title: "Scissors",
        subtitle: "Upload a picture of your hand doing the Scissors symbol: ✌️",
        buttonTitle: "Upload",
        nextStep: "lizard",
    },
    "lizard": {
        capture: true,
        title: "Lizard",
        subtitle: "Upload a picture of your hand doing the Lizard symbol: 🦎",
        buttonTitle: "Upload",
        nextStep: "spock",
    },
    "spock": {
        capture: true,
        title: "Spock",
        subtitle: "Upload a picture of your hand doing the Spock symbol: 🖖",
        buttonTitle: "Upload",
        nextStep: "congratulations",
    },
    "congratulations": {
        title: "Thank you!",
        subtitle: "Our neural nets love you.",
        buttonTitle: "Go again!",
        nextStep: "welcome",
    }
};
app.set('view engine', 'pug');
app.set('views', './views');

//SAVE CSS AND JS HERE
app.use('/public/', express.static(__dirname + '/public/'));
app.use('/data/', express.static(__dirname + '/data/'));
app.use('/data/', serveIndex(__dirname + '/data/', {
    'icons': true
}));

app.get('/', function(req, res) {
    res.redirect("/welcome")
})

app.get('/:step', function(req, res) {
    const stepName = req.params.step
    const step = steps[stepName];
    if (step != null) {
        step["name"] = stepName
        res.render('page', step)
    } else {
        res.redirect(301, "/welcome")
    }
});

app.post('/:step', function(req, res) {
    const stepName = req.params.step
    const step = steps[stepName];
    if (step != null && step.capture) {
        const busboy = new Busboy({
            headers: req.headers
        });

        // This code will process each file uploaded.
        busboy.on('file', (fieldname, file, filename) => {
            if (filename) {
                // Note: os.tmpdir() points to an in-memory file system on GCF
                // Thus, any files in it must fit in the instance's memory.
                console.log(`Processed file ${filename}`);
                const filepath = path.join(os.tmpdir(), filename);
                file.pipe(fs.createWriteStream(filepath));

                const options = {
                    destination: `/${stepName}/${Date.now()}.jpg`
                };

                bucket.upload(filepath, options, function(err, file) {
                    console.log("Uploaded")
                });
            }
        });

        // Triggered once all uploaded files are processed by Busboy.
        // We still need to wait for the disk writes (saves) to complete.
        busboy.on('finish', () => {
            res.writeHead(200, {
                'Content-Type': 'text/html'
            });
            res.write(`<script>setTimeout(function () { window.location.href = "/${step.nextStep}"; }, 100);</script>`);
            res.end();
        });

        busboy.end(req.rawBody);
    } else {
        res.writeHead(400, {
            'content-type': 'text/plain'
        });
        res.write('why are you doing this?\n\n');
        res.end();
    }
});

// app.listen(port, () => console.log(`Data collector app listening on port ${port}!`))
exports.app = functions.https.onRequest(app);