<html>
<body>
Some body text
  <span t:text="${welcome.message}" />
  <table>
    <tr t:each="student: ${students}">
        Some repeated text
      <td t:text="${student.id}" />
      <td t:text="${student.name}" />
    </tr>
  </table>

  <table t:if="${positiveNumber}">
      <tr t:each="student: ${students}">
        <td t:text="${student.id}" />
        <td t:text="${student.name}" />
      </tr>
    </table>
</body>
</html>