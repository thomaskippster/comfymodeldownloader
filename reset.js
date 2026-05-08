module.exports = {
  run: [
    {
      method: "fs.rm",
      params: {
        path: "env"
      }
    },
    {
      method: "fs.rm",
      params: {
        path: "target"
      }
    }
  ]
}
