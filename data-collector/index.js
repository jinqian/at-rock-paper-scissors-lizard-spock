const express = require('express')
const app = express()
const port = process.env.PORT || 4000

const formidable = require('formidable');
const fs = require('fs');
const serveIndex = require('serve-index');

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
        subtitle: "Upload a picture of your hand doing the Lizard symbol: 🦎 👌",
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
    res.redirect(302, "/welcome")
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
        const form = new formidable.IncomingForm()
        const stepFolderPath = `${__dirname}/data/${stepName}`
        const count = fs.readdirSync(stepFolderPath).length
        const uploadPath = `${stepFolderPath}/${count}.jpg`;
        form.on('fileBegin', function(name, file) {
            file.path = uploadPath
        });
        form.on('file', function(name, file) {
            console.log('Uploaded ' + uploadPath);
            res.writeHead(200, {
                'Content-Type': 'text/html'
            });
            res.write(`<script>setTimeout(function () { window.location.href = "/${step.nextStep}"; }, 100);</script>`);
            res.end();
        });
        form.parse(req, function(err, fields, files) {
            // fs.rename(files.upload.path, uploadPath, function (err) { throw err; });
        });

    } else {
        res.writeHead(400, {
            'content-type': 'text/plain'
        });
        res.write('why are you doing this?\n\n');
        res.end();
    }
});

app.listen(port, () => console.log(`Data collector app listening on port ${port}!`))