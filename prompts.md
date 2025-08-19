Break down my startup (project) idea into small steps. I want to make an Automated HandWritten exam answer sheet checking machine. We use our Android/IOS devices to scan the answer sheet, and the application will automatically grade each answer. 
For saving time we will be using Google vision api.
These are the things is will be able to extarct ->
  1. Roll Number, Name, Slot, Exam date
  2. Answer with the question/answer number tag

With the above extracted information, our application will create two things ->
  1. An Excel/Google sheet with columns student roll number, their marks for each question, slot, and exam date
  2. An annotated PDF of their answer sheet with an attached link to those places where the student lose marks
