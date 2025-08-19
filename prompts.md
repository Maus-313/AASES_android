Break down my startup (project) idea into small steps. I want to make an Automated HandWritten exam answer sheet checking machine. We use our Android/IOS devices to scan the answer sheet, and the application will automatically grade each answer. 
To save time, we will be using the Google vision api.

These are the things that will be able to extract ->
  1. Roll Number, Name, Slot, Exam date
  2. Answer with the question/answer number tag

With the above extracted information, our application will create two things ->
  1. An Excel/Google sheet with columns: student roll number, their marks for each question, slot, and exam date
  2. An annotated PDF of their answer sheet with an attached link to those places where the student lost marks

This will have two platforms, one a website and and mobile application

  1. The Website will show the output of our scan in a user(professor/ teacher) friendly manner, so that the user can easily see or download the Excel or PDF files.
  2. The Android/IOS device will be for the main scanning process. It will scan answer sheets at a high speed, and then the internal logic code will handle the conversion process.

Note -> The Android/IOS application can be either (Jetpack compose + Kotlin), Flutter, or a simple web application.
