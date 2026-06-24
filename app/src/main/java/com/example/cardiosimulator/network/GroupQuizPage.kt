package com.example.cardiosimulator.network

object GroupQuizPage {
    const val Html = """
<!DOCTYPE html>
<html lang="ru">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Тестирование</title>
    <style>
        body { font-family: -apple-system, system-ui, sans-serif; line-height: 1.5; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; background: #f4f4f9; }
        .card { background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); margin-bottom: 20px; }
        h1 { font-size: 1.5rem; margin-top: 0; color: #2c3e50; }
        input[type="text"] { width: 100%; padding: 10px; margin: 10px 0; border: 1px solid #ddd; border-radius: 4px; box-sizing: border-box; }
        button { background: #3498db; color: #fff; border: none; padding: 12px 20px; border-radius: 4px; cursor: pointer; width: 100%; font-size: 1rem; }
        button:hover { background: #2980b9; }
        .question { margin-bottom: 30px; }
        .question-text { font-weight: bold; margin-bottom: 10px; }
        .option { display: flex; align-items: center; margin: 8px 0; padding: 10px; border: 1px solid #eee; border-radius: 4px; cursor: pointer; }
        .option:hover { background: #f9f9f9; }
        .option input { margin-right: 15px; }
        img { max-width: 100%; height: auto; border-radius: 4px; margin: 10px 0; }
        #registration, #quiz, #result { display: none; }
        .status { text-align: center; color: #7f8c8d; font-size: 0.9rem; }
        .passed { color: #27ae60; font-weight: bold; }
        .failed { color: #e74c3c; font-weight: bold; }
    </style>
</head>
<body>
    <div id="registration" class="card" style="display: block;">
        <h1>Регистрация</h1>
        <input type="text" id="fullName" placeholder="ФИО">
        <input type="text" id="group" placeholder="Группа">
        <button id="startBtn" onclick="register()">Начать тест</button>
        <p id="regStatus" class="status"></p>
    </div>

    <div id="quiz">
        <h1 id="quizTitle">Тестирование</h1>
        <div id="questionsList"></div>
        <button id="submitBtn" onclick="submit()">Завершить и отправить</button>
    </div>

    <div id="result" class="card">
        <h1>Результат</h1>
        <p id="scoreText" style="font-size: 1.2rem; text-align: center;"></p>
        <p id="statusText" style="text-align: center; font-size: 1.5rem;"></p>
    </div>

    <script>
        let token = null;
        let questions = [];

        async function register() {
            const fullName = document.getElementById('fullName').value.trim();
            const group = document.getElementById('group').value.trim();
            if (!fullName || !group) {
                alert('Пожалуйста, введите ФИО и группу');
                return;
            }

            document.getElementById('startBtn').disabled = true;
            document.getElementById('regStatus').innerText = 'Загрузка...';

            try {
                const res = await fetch('/api/register', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ fullName, group })
                });
                if (!res.ok) throw new Error(await res.text());
                const data = await res.json();
                token = data.token;
                questions = data.questions;
                showQuiz();
            } catch (e) {
                alert('Ошибка: ' + e.message);
                document.getElementById('startBtn').disabled = false;
                document.getElementById('regStatus').innerText = '';
            }
        }

        function showQuiz() {
            document.getElementById('registration').style.display = 'none';
            document.getElementById('quiz').style.display = 'block';
            
            const list = document.getElementById('questionsList');
            list.innerHTML = '';
            
            questions.forEach((q, i) => {
                const qDiv = document.createElement('div');
                qDiv.className = 'card question';
                
                let html = '<div class="question-text">' + (i + 1) + '. ' + escape(q.text) + '</div>';
                
                if (q.stimulus === 'Image' && q.imagePath) {
                    html += '<img src="/api/image?token=' + token + '&qid=' + q.id + '">';
                } else if (q.stimulus === 'Ecg') {
                    html += '<p style="font-style:italic; color:#7f8c8d;">(ЭКГ доступна только на мониторе преподавателя)</p>';
                }
                
                q.options.forEach(opt => {
                    html += '<label class="option">' +
                            '<input type="radio" name="q_' + q.id + '" value="' + opt.id + '">' +
                            '<span>' + escape(opt.text) + '</span>' +
                            '</label>';
                });
                
                qDiv.innerHTML = html;
                list.appendChild(qDiv);
            });
        }

        async function submit() {
            if (!confirm('Вы уверены, что хотите завершить тест?')) return;
            
            const selections = {};
            questions.forEach(q => {
                const selected = document.querySelector('input[name="q_' + q.id + '"]:checked');
                if (selected) selections[q.id] = selected.value;
            });

            document.getElementById('submitBtn').disabled = true;

            try {
                const res = await fetch('/api/submit', {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ token, selections })
                });
                if (!res.ok) throw new Error(await res.text());
                const data = await res.json();
                showResult(data);
            } catch (e) {
                alert('Ошибка при отправке: ' + e.message);
                document.getElementById('submitBtn').disabled = false;
            }
        }

        function showResult(data) {
            document.getElementById('quiz').style.display = 'none';
            document.getElementById('result').style.display = 'block';
            
            document.getElementById('scoreText').innerText = 'Ваш результат: ' + data.correct + ' из ' + data.total;
            const status = document.getElementById('statusText');
            if (data.passed) {
                status.innerText = 'СДАНО';
                status.className = 'passed';
            } else {
                status.innerText = 'НЕ СДАНО';
                status.className = 'failed';
            }
        }

        function escape(s) {
            return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        }
    </script>
</body>
</html>
"""
}
